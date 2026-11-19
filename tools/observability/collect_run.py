#!/usr/bin/env python3
"""One-shot, read-only evidence collector for one delegated review wave.

Reads:  a private authorization manifest (JSON), the explicit read-only Hermes
        state database, optionally a coordinator event log and a command ledger.
Writes: sanitized, deterministic metric files into an allowed metrics directory.

Design constraints that are deliberate and not configurable:
  * stdlib only, no network, no polling, no daemon, no sleeps;
  * one execution = one snapshot; nothing waits for a running wave to finish;
  * the database is opened read-only with a whitelist of metadata columns;
    goals, prompts, transcripts, tool payloads and system prompts are never read;
  * an absent record is never reported as a finished/PASS result: unknown or
    running states stay null/``pending``;
  * every published file is sanitized (paths, host, emails, token-like strings)
    and carries a source digest plus a transformation disclosure.

Exit codes: 0 = snapshot written, 2 = usage/validation error, 3 = source unreadable.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

SCHEMA_VERSION = 1

# Columns read from Hermes. Anything not listed is never selected.
DELEGATION_COLUMNS = (
    "delegation_id",
    "origin_session",
    "parent_session_id",
    "state",
    "dispatched_at",
    "completed_at",
)
SESSION_COLUMNS = (
    "id",
    "parent_session_id",
    "model",
    "billing_provider",
    "started_at",
    "ended_at",
    "tool_call_count",
    "api_call_count",
    "input_tokens",
    "output_tokens",
    "cache_read_tokens",
    "cache_write_tokens",
    "actual_cost_usd",
    "cost_status",
    "end_reason",
)
# Soft-required columns: absence is recorded as a schema gap, never fatal.
SESSION_OPTIONAL = ("api_call_count", "cache_read_tokens", "cache_write_tokens", "end_reason")

# Free text that may survive sanitization is checked against these patterns.
REDACTIONS = (
    (re.compile(r"[A-Za-z]:[\\/]+Users[\\/]+[^\\/\s\"'<>|]+", re.I), "<user-home>"),
    (re.compile(r"/(?:home|Users)/[^/\s\"'<>|]+"), "<posix-home>"),
    (re.compile(r"\\\\[A-Za-z0-9._-]+\\[A-Za-z0-9$_-]+"), "<unc-share>"),
    (re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"), "<email>"),
    (re.compile(r"(?i)\b(bearer)\s+[A-Za-z0-9._~+/=-]{8,}"), r"\1 <redacted-token>"),
    (re.compile(r"\b(?:sk|ghp|gho|ghs|xoxb|xoxp|akia)[-_A-Za-z0-9]{10,}\b", re.I), "<redacted-token>"),
    (
        re.compile(r"(?i)\b(password|passwd|secret|token|api[_-]?key|authorization)\b\s*[:=]\s*\S+"),
        r"\1=<redacted>",
    ),
    (re.compile(r"(?i)\b(hostname|computername)\b\s*[:=]\s*[^\s,\"']+"), r"\1=<host>"),
)

SEMANTIC_KEYS = ("hermes_home", "private_dir", "runtime_dir", "repo")

COMMAND_REQUIRED = ("worker", "phase", "attempt", "start_utc", "exit_code")


class CollectError(Exception):
    """Validation or source problem; reported without a traceback in normal use."""


# --------------------------------------------------------------------------- io


def pseudonym_for(run_id, raw_id):
    """Deterministic, one-way worker pseudonym: stable across public files."""
    return "w-%s" % sha256_text("%s|%s" % (run_id, raw_id))[:10]


def utc(value):
    """Epoch seconds -> UTC ISO-8601 with microseconds; None stays None."""
    if value is None:
        return None
    return dt.datetime.fromtimestamp(float(value), dt.timezone.utc).isoformat()


def parse_utc(text):
    if text is None:
        return None
    text = str(text).strip().replace("Z", "+00:00")
    parsed = dt.datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=dt.timezone.utc)
    return parsed.astimezone(dt.timezone.utc)


def epoch(iso_text):
    return parse_utc(iso_text).timestamp()


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def dump_json(path, data):
    text = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    return sha256_text(text)


# ------------------------------------------------------------------ sanitizing


def sanitizer(manifest):
    """Build a text scrubber using semantic names from the manifest."""
    pairs = []
    for key in SEMANTIC_KEYS:
        value = manifest.get(key)
        if isinstance(value, str) and len(value) > 3:
            pairs.append((value.replace("\\", "/"), "<%s>" % key.replace("_", "-")))
    pairs.sort(key=lambda item: len(item[0]), reverse=True)

    def scrub(text):
        if text is None:
            return None
        text = str(text)
        for raw, placeholder in pairs:
            text = text.replace(raw, placeholder)
            text = text.replace(raw.replace("/", "\\"), placeholder)
        for pattern, replacement in REDACTIONS:
            text = pattern.sub(replacement, text)
        return text

    return scrub


def contains_secret_like(text):
    """Used by the collector and by tests: True if any redaction pattern matches."""
    return any(pattern.search(text or "") for pattern, _ in REDACTIONS)


# ------------------------------------------------------------------- intervals


def interval_stats(intervals):
    """Statistics over possibly overlapping [start, end] intervals in seconds.

    ``overlapping_sum_seconds`` is a naive sum and is NOT additive work time;
    ``union_seconds`` de-overlaps. ``peak`` counts simultaneous intervals.
    Open intervals (end == clip) are reported separately and never counted as
    verified completed durations.
    """
    usable = [(a, b) for a, b, _ in intervals if b is not None and b > a]
    naive = sum(b - a for a, b in usable)
    merged = []
    for a, b in sorted(usable):
        if merged and a <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], b)
        else:
            merged.append([a, b])
    union = sum(b - a for a, b in merged)
    events = sorted([(a, 1) for a, b in usable] + [(b, -1) for a, b in usable])
    current = peak = 0
    for _, delta in events:
        current += delta
        peak = max(peak, current)
    return {
        "count": len(usable),
        "overlapping_sum_seconds": round(naive, 6),
        "union_seconds": round(union, 6),
        "union_intervals": [[round(a, 6), round(b, 6)] for a, b in merged],
        "peak_concurrent": peak,
    }


# ---------------------------------------------------------------- db access


def open_readonly(path):
    resolved = Path(path)
    if not resolved.is_file():
        raise CollectError("state database not found: %s" % sanitize_path_hint(resolved))
    uri = resolved.resolve().as_uri() + "?mode=ro&immutable=0"
    conn = sqlite3.connect(uri, uri=True, timeout=5)
    conn.row_factory = sqlite3.Row
    return conn


def sanitize_path_hint(path):
    """Never echo a full local path in an error message."""
    return "<state-db>" if Path(path).exists() else "<state-db:missing>"


def table_columns(conn, table):
    rows = conn.execute("PRAGMA table_info(%s)" % table).fetchall()
    if not rows:
        raise CollectError("required table missing: %s" % table)
    return {row["name"] for row in rows}


def fetch_delegation(conn, delegation_id):
    columns = table_columns(conn, "async_delegations")
    missing = [c for c in DELEGATION_COLUMNS if c not in columns]
    if missing:
        raise CollectError("async_delegations missing columns: %s" % ",".join(missing))
    sql = "SELECT %s FROM async_delegations WHERE delegation_id = ?" % ",".join(DELEGATION_COLUMNS)
    row = conn.execute(sql, (delegation_id,)).fetchone()
    return (dict(row) if row else None), missing


def fetch_task_results(conn, delegation_id, delegation_row, session_ids):
    """Whitelist-only view of the batch task results. Returns (tasks, gaps).

    Task results are matched to runtime sessions only by dispatch order, which
    is reported as an unverified hypothesis; no token-based join is attempted.
    """
    gaps = []
    columns = table_columns(conn, "async_delegations")
    if "result_json" not in columns or "event_json" not in columns:
        return [], ["async_delegations.result_json/event_json absent"]
    row = conn.execute(
        "SELECT result_json, event_json, task_json FROM async_delegations WHERE delegation_id = ?",
        (delegation_id,),
    ).fetchone()
    if row is None:
        return [], ["delegation row disappeared between reads"]
    payload = row["result_json"] or row["event_json"]
    if not payload:
        return [], ["delegation has no result payload yet (state=%s)" % (delegation_row or {}).get("state")]
    try:
        parsed = json.loads(payload)
    except ValueError as error:
        return [], ["result payload is not valid JSON: %s" % type(error).__name__]
    results = parsed.get("results") if isinstance(parsed, dict) else None
    if not isinstance(results, list):
        return [], ["result payload has no results list"]
    tasks = []
    for entry in sorted(results, key=lambda item: item.get("task_index", 0)):
        tokens = entry.get("tokens") or {}
        trace = entry.get("tool_trace")
        tasks.append(
            {
                "task_index": entry.get("task_index"),
                "status": entry.get("status"),
                "model": entry.get("model"),
                "duration_seconds": entry.get("duration_seconds"),
                "api_calls": entry.get("api_calls"),
                "exit_reason": entry.get("exit_reason"),
                "truncated": entry.get("truncated"),
                "tool_trace_entries": len(trace) if isinstance(trace, list) else None,
                "tokens_reported": {
                    "input": tokens.get("input"),
                    "output": tokens.get("output"),
                    "meaning": "agent-reported task counters; not billable usage",
                },
                "cost_usd": entry.get("cost_usd"),
                "cost_status": entry.get("cost_status"),
                "session_hypothesis": session_ids[entry.get("task_index", -1)]
                if isinstance(entry.get("task_index"), int) and 0 <= entry["task_index"] < len(session_ids)
                else None,
                "session_join": "positional_hypothesis_unverified",
            }
        )
    if len(session_ids) != len(tasks):
        gaps.append(
            "task/session count mismatch: %d task results vs %d runtime sessions"
            % (len(tasks), len(session_ids))
        )
    return tasks, gaps


def fetch_sessions(conn, parent_session_id, window_start, window_end):
    columns = table_columns(conn, "sessions")
    missing_required = [c for c in ("id", "started_at", "ended_at") if c not in columns]
    if missing_required:
        raise CollectError("sessions missing columns: %s" % ",".join(missing_required))
    selected = [c for c in SESSION_COLUMNS if c in columns]
    schema_missing = [c for c in SESSION_COLUMNS if c not in columns]
    sql = "SELECT %s FROM sessions WHERE parent_session_id = ? AND started_at >= ? AND started_at <= ? ORDER BY started_at, id" % ",".join(selected)
    rows = [dict(row) for row in conn.execute(sql, (parent_session_id, window_start, window_end))]
    for row in rows:
        for name in schema_missing:
            row.setdefault(name, None)
    return rows, schema_missing


# -------------------------------------------------------------------- commands


def load_commands(path, scrub):
    """Validate an optional sanitized command ledger (JSONL).

    Missing fields are collected into ``incomplete`` and the record is kept —
    a command with no exit code is not a successful command.
    """
    records, problems = [], []
    if path is None:
        return records, problems
    source = Path(path)
    if not source.is_file():
        raise CollectError("command ledger not found: <commands>")
    for number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            record = json.loads(line)
        except ValueError:
            problems.append({"line": number, "error": "invalid JSON"})
            continue
        missing = [field for field in COMMAND_REQUIRED if record.get(field) in (None, "")]
        if missing:
            record["incomplete"] = missing
        record["argv"] = scrub(record.get("argv"))
        record["cwd"] = scrub(record.get("cwd"))
        record["worker"] = scrub(record.get("worker"))
        record["phase"] = scrub(record.get("phase"))
        if record.get("start_utc") and record.get("end_utc"):
            record["observed_duration_seconds"] = round(
                epoch(record["end_utc"]) - epoch(record["start_utc"]), 6
            )
        else:
            record["observed_duration_seconds"] = None
        records.append(record)
    return records, problems


def command_summary(records, problems, pending_reason):
    completed = [r for r in records if r.get("exit_code") is not None]
    incomplete = sum(1 for r in records if r.get("incomplete"))
    if not records:
        verdict = "pending_source"
    elif problems or incomplete or len(completed) != len(records):
        verdict = "partial_observation"
    else:
        verdict = "observed"
    return {
        "records": len(records),
        "with_exit_code": len(completed),
        "exit_zero": sum(1 for r in completed if r.get("exit_code") == 0),
        "nonzero": sum(1 for r in completed if r.get("exit_code") != 0),
        "incomplete_records": incomplete,
        "parse_problems": problems,
        "phase_totals": phase_totals(records),
        "verdict": verdict,
        "pending_reason": None if verdict == "observed" else pending_reason,
    }


def phase_totals(records):
    totals = {}
    for record in records:
        phase = record.get("phase") or "unlabelled"
        bucket = totals.setdefault(phase, {"commands": 0, "duration_seconds": 0.0, "exits": []})
        bucket["commands"] += 1
        if record.get("observed_duration_seconds"):
            bucket["duration_seconds"] = round(
                bucket["duration_seconds"] + record["observed_duration_seconds"], 6
            )
        bucket["exits"].append(record.get("exit_code"))
    return totals


# ------------------------------------------------------------------ snapshot


def collect(args):
    manifest_path = Path(args.manifest)
    if not manifest_path.is_file():
        raise CollectError("manifest not found: <manifest>")
    manifest_text = manifest_path.read_text(encoding="utf-8")
    manifest = json.loads(manifest_text)
    scrub = sanitizer(manifest)

    authorization = manifest.get("authorization") or {}
    authorized_at = parse_utc(authorization.get("timestamp_utc"))
    if authorized_at is None:
        raise CollectError("manifest.authorization.timestamp_utc is required")
    deadline = parse_utc(manifest.get("deadline_utc"))
    preflight = parse_utc(manifest.get("first_preflight_observed_at_utc"))
    bootstrap = parse_utc(manifest.get("bootstrap_started_at_utc"))

    state_db = Path(args.state_db)
    db_sha = sha256_file(state_db) if state_db.is_file() else None
    conn = open_readonly(state_db)
    try:
        delegation_row, delegation_missing = fetch_delegation(conn, args.delegation_id)
        if delegation_row is None:
            raise CollectError(
                "delegation %s not present in state database (not treated as completion)"
                % args.delegation_id
            )
        dispatched_at = parse_utc(utc(delegation_row.get("dispatched_at")))
        completed_raw = delegation_row.get("completed_at")
        completed_at = parse_utc(utc(completed_raw)) if completed_raw else None
        as_of = parse_utc(args.as_of) if args.as_of else dt.datetime.now(dt.timezone.utc)
        if completed_at is not None and completed_at < as_of:
            stage_end = completed_at
            stage_basis = "delegation.completed_at"
        else:
            stage_end = as_of
            stage_basis = "snapshot_wall_clock (wave still running: not a completed wave)"

        parent = delegation_row.get("parent_session_id") or delegation_row.get("origin_session")
        rows, schema_missing = fetch_sessions(
            conn, parent, dispatched_at.timestamp() - 120, stage_end.timestamp() + 60
        )
        session_ids = [row["id"] for row in rows]
        session_pseudonyms = [pseudonym_for(manifest.get("run_id"), sid) for sid in session_ids]
        tasks, task_gaps = fetch_task_results(
            conn, args.delegation_id, delegation_row, session_pseudonyms
        )
    finally:
        conn.close()

    window_start = dispatched_at.timestamp()
    window_end = stage_end.timestamp()

    workers = []
    for index, row in enumerate(rows, 1):
        started = row.get("started_at")
        ended = row.get("ended_at")
        open_interval = ended is None
        has_start = started is not None
        end_for_math = window_end if open_interval else min(float(ended), window_end)
        begin_for_math = max(float(started), window_start) if has_start else None
        interval_seconds = (
            round(end_for_math - begin_for_math, 6)
            if has_start and end_for_math >= begin_for_math else None
        )
        if not has_start:
            interval_status = "missing_start"
        elif open_interval:
            interval_status = "open_at_snapshot"
        else:
            interval_status = "closed"
        workers.append(
            {
                "pseudonym": pseudonym_for(manifest.get("run_id"), row["id"]),
                "identity_sha256": sha256_text(str(row["id"])),
                "order": index,
                "label": None,
                "model": row.get("model"),
                "provider": row.get("billing_provider"),
                "started_at_utc": utc(started) if has_start else None,
                "ended_at_utc": utc(ended),
                "open_interval": open_interval,
                "interval_seconds": interval_seconds,
                "interval_status": interval_status,
                "tool_calls": row.get("tool_call_count"),
                "api_calls": row.get("api_call_count"),
                "cache_read_tokens": row.get("cache_read_tokens"),
                "cache_write_tokens": row.get("cache_write_tokens"),
                "output_tokens": row.get("output_tokens"),
                "cost_usd": None,
                "cost_status": row.get("cost_status") or "unknown",
                "end_reason": row.get("end_reason"),
                "source": "hermes.sessions",
            }
        )

    roster = {}
    if args.roster:
        roster_path = Path(args.roster)
        if not roster_path.is_file():
            raise CollectError("roster not found: <roster>")
        raw_roster = json.loads(roster_path.read_text(encoding="utf-8"))
        roster = raw_roster.get("labels") if isinstance(raw_roster, dict) else None
        if isinstance(raw_roster, dict) and "workers" in raw_roster:
            roster = raw_roster["workers"]
    if isinstance(roster, list):
        for worker, label in zip(workers, roster):
            worker["label"] = scrub(label)
    elif isinstance(roster, dict):
        for worker in workers:
            worker["label"] = scrub(roster.get(str(worker["order"])))

    intervals = []
    for worker in workers:
        if not worker["started_at_utc"]:
            continue
        begin = max(epoch(worker["started_at_utc"]), window_start)
        end = epoch(worker["ended_at_utc"]) if worker["ended_at_utc"] else window_end
        intervals.append((begin, min(end, window_end), worker["pseudonym"]))
    stats = interval_stats(intervals)

    coordinator_events = []
    if args.coordinator_events:
        events_path = Path(args.coordinator_events)
        if not events_path.is_file():
            raise CollectError("coordinator events not found: <events>")
        coordinator_events = [
            {k: scrub(v) if isinstance(v, str) else v for k, v in json.loads(line).items()}
            for line in events_path.read_text(encoding="utf-8").splitlines() if line.strip()
        ]

    command_records, command_problems = load_commands(args.commands, scrub)
    commands = command_summary(
        command_records,
        command_problems,
        "worker command ledgers are private until the coordinator publishes them; "
        "absence is not a pass",
    )

    events = [
        {
            "event_id": "R1-0001",
            "type": "authorization",
            "stage": "R1",
            "ts_utc": authorized_at.isoformat(),
            "clock_source": authorization.get("timestamp_source"),
            "source": "private authorization manifest (sanitized derivation)",
        },
        {
            "event_id": "R1-0002",
            "type": "preflight_observed",
            "stage": "R1",
            "ts_utc": preflight.isoformat() if preflight else None,
            "offset_from_authorization_seconds": round((preflight - authorized_at).total_seconds(), 6)
            if preflight else None,
            "source": "coordinator observation",
        },
        {
            "event_id": "R1-0003",
            "type": "bootstrap_started",
            "stage": "R1",
            "ts_utc": bootstrap.isoformat() if bootstrap else None,
            "offset_from_authorization_seconds": round((bootstrap - authorized_at).total_seconds(), 6)
            if bootstrap else None,
            "source": "coordinator observation",
        },
        {
            "event_id": "R1-0004",
            "type": "dispatch",
            "stage": args.stage,
            "ts_utc": dispatched_at.isoformat(),
            "delegation_state": delegation_row.get("state"),
            "worker_count": len(workers),
            "orchestration_before_dispatch_seconds": round(
                (dispatched_at - authorized_at).total_seconds(), 6
            ),
            "source": "hermes.async_delegations",
        },
    ]
    for worker in workers:
        events.append(
            {
                "event_id": "R1-%04d" % (100 + worker["order"] * 2),
                "type": "worker_start",
                "stage": args.stage,
                "ts_utc": worker["started_at_utc"],
                "worker": worker["pseudonym"],
                "label": worker["label"],
                "model": worker["model"],
                "provider": worker["provider"],
                "source": "hermes.sessions",
            }
        )
        events.append(
            {
                "event_id": "R1-%04d" % (101 + worker["order"] * 2),
                "type": "worker_end" if not worker["open_interval"] else "worker_still_running_at_snapshot",
                "stage": args.stage,
                "ts_utc": worker["ended_at_utc"] or stage_end.isoformat(),
                "worker": worker["pseudonym"],
                "duration_seconds": worker["interval_seconds"],
                "interval_status": worker["interval_status"],
                "tool_calls": worker["tool_calls"],
                "api_calls": worker["api_calls"],
                "source": "hermes.sessions",
            }
        )
    events.append(
        {
            "event_id": "R1-9000",
            "type": "wave_completed" if completed_at else "snapshot_taken",
            "stage": args.stage,
            "ts_utc": stage_end.isoformat(),
            "stage_basis": stage_basis,
            "delegation_state": delegation_row.get("state"),
            "source": "hermes.async_delegations",
        }
    )
    for extra in coordinator_events:
        events.append(
            {
                "event_id": extra.get("event_id"),
                "type": extra.get("type"),
                "stage": extra.get("stage"),
                "ts_utc": extra.get("ts_utc"),
                "clock_source": extra.get("clock_source"),
                "duration_seconds": extra.get("duration_seconds"),
                "source": "coordinator event log (sanitized derivation)",
            }
        )
    events.sort(key=lambda item: (item.get("ts_utc") or "", item.get("event_id") or ""))

    elapsed = (stage_end - authorized_at).total_seconds()
    orchestration = (dispatched_at - authorized_at).total_seconds()
    running = delegation_row.get("state") != "completed" or completed_at is None
    gaps = list(task_gaps)
    if running:
        gaps.append("wave running at snapshot: wall/stage totals are lower bounds, not a result")
    if schema_missing:
        gaps.append("sessions columns absent: %s" % ",".join(schema_missing))
    if not workers:
        gaps.append("no worker sessions observed in the dispatch window")
    if commands["verdict"] != "observed":
        gaps.append(
            "worker command ledger %s (verdict=%s): not every command has a verified exit code"
            % ("pending" if commands["verdict"] == "pending_source" else "incomplete", commands["verdict"])
        )
    gaps.append("task result -> session mapping is positional only and unverified")
    gaps.append("cost and tokens are unknown; absence of a price is not a zero price")
    gaps.append("no review/rework acceptance record exists yet")

    run = {
        "schema_version": SCHEMA_VERSION,
        "stage": args.stage,
        "run_id": manifest.get("run_id"),
        "delivery_status": "draft_snapshot",
        "accepted": False,
        "accepted_note": "snapshot from a read-only collector; acceptance is the coordinator's verdict, not this file's",
        "repo_semantic": "<repo>",
        "base_sha": manifest.get("base_sha"),
        "delegation": {
            "delegation_id": args.delegation_id,
            "state": delegation_row.get("state"),
            "dispatched_at_utc": dispatched_at.isoformat(),
            "completed_at_utc": completed_at.isoformat() if completed_at else None,
            "origin_session_pseudonym": "s-%s" % sha256_text(str(parent))[:10],
            "worker_sessions_observed": len(workers),
            "source": "hermes.async_delegations + hermes.sessions (metadata whitelist)",
        },
        "clocks": {
            "authorization_utc": authorized_at.isoformat(),
            "authorization_source": authorization.get("timestamp_source"),
            "first_preflight_utc": preflight.isoformat() if preflight else None,
            "bootstrap_utc": bootstrap.isoformat() if bootstrap else None,
            "dispatch_utc": dispatched_at.isoformat(),
            "snapshot_utc": as_of.isoformat(),
            "snapshot_kind": "fixed --as-of" if args.as_of else "wall clock at run",
            "deadline_utc": deadline.isoformat() if deadline else None,
            "elapsed_from_authorization_seconds": round(elapsed, 6),
            "orchestration_before_dispatch_seconds": round(orchestration, 6),
            "orchestration_share_of_elapsed": round(orchestration / elapsed, 6) if elapsed > 0 else None,
            "remaining_to_deadline_seconds": round((deadline - as_of).total_seconds(), 6) if deadline else None,
        },
        "stage_window": {
            "start_utc": dispatched_at.isoformat(),
            "end_utc": stage_end.isoformat(),
            "wall_seconds": round(window_end - window_start, 6),
            "basis": stage_basis,
            "completed": not running,
            "lower_bound": running,
        },
        "concurrency": {
            "worker_intervals": stats["count"],
            "overlapping_sum_seconds_not_additive": stats["overlapping_sum_seconds"],
            "union_seconds": stats["union_seconds"],
            "union_intervals_utc": [
                [utc(a), utc(b)] for a, b in stats["union_intervals"]
            ],
            "peak_concurrent_workers": stats["peak_concurrent"],
            "average_active_workers": round(stats["union_seconds"] / (window_end - window_start), 6)
            if window_end > window_start else None,
            "note": "sum of intervals double-counts overlap; only the union is additive wall time",
        },
        "workers": workers,
        "tasks": tasks,
        "commands": commands,
        "review": {
            "status": "pending",
            "accepted_items": [],
            "rework_events": [],
            "note": "no acceptance or rework record exists at snapshot time; pending is not zero",
        },
        "cost": {
            "usd": None,
            "status": "unknown",
            "note": "runtime reports no actual cost; a provider route does not prove zero price "
                    "or remaining quota",
        },
        "tokens": {
            "billable": None,
            "note": "per-session counters and per-task agent counters have different semantics; "
                    "not reconciled and not presented as usage",
        },
        "gaps": gaps,
        "files": {},
    }

    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    scrub_output_path = output

    run["files"] = {
        "run.json": "run.json",
        "workers.json": "workers.json",
        "events.jsonl": "events.jsonl",
        "commands.txt": "commands.txt",
        "provenance.json": "provenance.json",
    }
    hashes = {}
    hashes["run.json"] = dump_json(scrub_output_path / "run.json", run)

    workers_doc = {
        "schema_version": SCHEMA_VERSION,
        "run_id": manifest.get("run_id"),
        "stage": args.stage,
        "delivery_status": "draft_snapshot",
        "roster_status": "labels from private roster file; order by started_at"
        if roster else "no roster supplied: labels null",
        "session_join": "positional_hypothesis_unverified",
        "workers": [
            {k: v for k, v in worker.items() if k != "identity_sha256"} for worker in workers
        ],
    }
    hashes["workers.json"] = dump_json(scrub_output_path / "workers.json", workers_doc)

    events_text = "".join(
        json.dumps(event, ensure_ascii=False, sort_keys=True) + "\n" for event in events
    )
    (scrub_output_path / "events.jsonl").write_text(events_text, encoding="utf-8", newline="\n")
    hashes["events.jsonl"] = sha256_text(events_text)

    commands_text = render_commands(command_records, commands)
    (scrub_output_path / "commands.txt").write_text(commands_text, encoding="utf-8", newline="\n")
    hashes["commands.txt"] = sha256_text(commands_text)

    sources = [
        {
            "role": "authorization manifest",
            "semantic": "<private-run-dir>/authorization.json",
            "sha256": sha256_text(manifest_text),
            "bytes": len(manifest_text.encode("utf-8")),
            "read": "authorization clocks, base sha, run id; no free-text goals published",
        },
        {
            "role": "hermes state database",
            "semantic": "<hermes-home>/state.db",
            "sha256": db_sha,
            "bytes": state_db.stat().st_size if state_db.is_file() else None,
            "read": "read-only; whitelisted metadata columns of async_delegations and sessions",
        },
    ]
    if args.coordinator_events:
        events_source = Path(args.coordinator_events)
        sources.append(
            {
                "role": "coordinator event log",
                "semantic": "<private-run-dir>/coordinator-events.jsonl",
                "sha256": sha256_file(events_source),
                "bytes": events_source.stat().st_size,
                "read": "event type, timestamp, stage, duration; identifiers scrubbed",
            }
        )
    if args.commands:
        sources.append(
            {
                "role": "command ledger",
                "semantic": "<private-run-dir>/commands/*.jsonl",
                "sha256": sha256_file(Path(args.commands)),
                "bytes": Path(args.commands).stat().st_size,
                "read": "argv scrubbed, cwd replaced by semantic name",
            }
        )

    provenance = {
        "schema_version": SCHEMA_VERSION,
        "run_id": manifest.get("run_id"),
        "stage": args.stage,
        "delivery_status": "draft_snapshot",
        "collector": {
            "path": "tools/observability/collect_run.py",
            "sha256": sha256_file(Path(__file__)),
            "stdlib_only": True,
            "network": False,
            "polling": False,
            "invocation_template": (
                "python tools/observability/collect_run.py --manifest <private-run-dir>/authorization.json "
                "--delegation-id <id> --state-db <hermes-home>/state.db "
                "--output metrics/<run-id> [--as-of <utc>] [--roster <private>] "
                "[--coordinator-events <private>] [--commands <private>]"
            ),
        },
        "sources": sources,
        "transformation_disclosure": [
            "local absolute paths replaced by semantic names (<repo>, <hermes-home>, <private-run-dir>, <runtime-dir>, <user-home>)",
            "email addresses, host-share names, bearer tokens, key-like strings and password assignments redacted",
            "session identifiers replaced by deterministic pseudonyms plus a one-way identity_sha256",
            "task prompts, goals, transcripts, tool payloads, system prompts and per-message content never read or written",
            "durations recomputed from timestamps; no duration is copied from a self-report",
            "unknown or missing values stay null; running intervals are labelled open_at_snapshot",
        ],
        "integrity": {
            "output_sha256": hashes,
            "hashes_are_of_utf8_bytes_with_lf_newlines": True,
            "provenance_self_hash": "not included (would be self-referential)",
        },
    }
    dump_json(scrub_output_path / "provenance.json", provenance)

    # Byte-level determinism is proven by test_collect_run.py, which runs the
    # collector twice over one frozen fixture and compares output hashes.

    return {
        "run_id": run["run_id"],
        "delegation_state": run["delegation"]["state"],
        "workers_observed": len(workers),
        "wall_seconds": run["stage_window"]["wall_seconds"],
        "wall_basis": stage_basis,
        "union_seconds": stats["union_seconds"],
        "peak_concurrent": stats["peak_concurrent"],
        "commands_verdict": commands["verdict"],
        "output": args.output,
        "hashes": hashes,
    }


def render_commands(records, summary):
    lines = ["# Command ledger (sanitized derivation)", ""]
    lines.append("verdict: %s" % summary["verdict"])
    if summary.get("pending_reason"):
        lines.append("pending_reason: %s" % summary["pending_reason"])
    lines.append("")
    if not records:
        lines.append("No command records were available to the collector at snapshot time.")
        lines.append("This is an absence of evidence, not evidence of a clean run.")
        return "\n".join(lines) + "\n"
    header = "worker | phase | attempt | start_utc | end_utc | duration_s | timeout_s | exit | argv | log"
    lines.append(header)
    lines.append("-" * len(header))
    for record in records:
        lines.append(
            " | ".join(
                str(value)
                for value in (
                    record.get("worker"),
                    record.get("phase"),
                    record.get("attempt"),
                    record.get("start_utc"),
                    record.get("end_utc"),
                    record.get("observed_duration_seconds"),
                    record.get("timeout_seconds"),
                    record.get("exit_code"),
                    record.get("argv"),
                    record.get("log_file"),
                )
            )
        )
    return "\n".join(lines) + "\n"


def build_parser():
    parser = argparse.ArgumentParser(
        description="One-shot read-only collector for a delegated review wave; writes sanitized metrics.",
    )
    parser.add_argument("--manifest", required=True, help="private authorization.json")
    parser.add_argument("--delegation-id", required=True, help="actual Hermes delegation id")
    parser.add_argument("--state-db", required=True, help="explicit read-only path to state.db")
    parser.add_argument("--output", required=True, help="allowed metrics directory")
    parser.add_argument("--as-of", default=None, help="fixed UTC snapshot instant (deterministic runs)")
    parser.add_argument("--roster", default=None, help="optional private worker label map")
    parser.add_argument("--coordinator-events", default=None, help="optional private events JSONL")
    parser.add_argument("--commands", default=None, help="optional sanitized command ledger JSONL")
    parser.add_argument("--stage", default="R1", help="stage label written into outputs")
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        result = collect(args)
    except CollectError as error:
        print(json.dumps({"error": str(error), "status": "failed"}, ensure_ascii=False), file=sys.stderr)
        return 2
    except sqlite3.Error as error:
        print(json.dumps({"error": "sqlite: %s" % type(error).__name__, "status": "failed"}), file=sys.stderr)
        return 3
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
