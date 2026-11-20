#!/usr/bin/env python3
"""S10 checker: three plated-shield sprites, item model graph, codec id, preview.

stdlib only. Prints "OK" and exits 0 on success; prints "FAIL <reason>" and
exits 1 on the first problem.
"""
import json
import struct
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TEX = ROOT / "src/main/resources/assets/minemod/textures/entity/shield"
ITEM = ROOT / "src/main/resources/assets/minemod/items/plated_shield.json"
RENDERER = ROOT / "src/client/java/dev/minemod/client/shield/PlatedShieldSpecialRenderer.java"
CLIENT = ROOT / "src/client/java/dev/minemod/client/shield/PlatedShieldClient.java"
PREVIEW = ROOT / "evidence/S10/preview.png"
SPRITES = ("plated_shield_base.png", "plated_shield_base_nopattern.png", "plated_shield_overlay.png")
SIG = b"\x89PNG\r\n\x1a\n"
# Vanilla ShieldModel.createLayer(): plate texOffs(0,0), box 12x22x1.
FX0, FX1, FY0, FY1 = 1, 13, 1, 23  # front face
CX0, CX1, CY0, CY1 = 3, 11, 3, 21  # open centre of the overlay

class Bad(Exception):
    pass

def fail(reason):
    print("FAIL " + reason)
    raise SystemExit(1)

def chunks(data):
    pos = 8
    while pos + 8 <= len(data):
        size = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        yield kind, data[pos + 8:pos + 8 + size]
        pos += 12 + size
        if kind == b"IEND":
            return
    raise Bad("missing IEND chunk")

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c

def read_png(name):
    path = TEX / name
    if not path.is_file():
        raise Bad("missing texture: " + path.relative_to(ROOT).as_posix())
    data = path.read_bytes()
    if data[:8] != SIG:
        raise Bad("bad PNG signature: " + name)
    ihdr, idat = None, bytearray()
    for kind, payload in chunks(data):
        if kind == b"IHDR":
            ihdr = payload
        elif kind == b"IDAT":
            idat += payload
    if ihdr is None or len(ihdr) != 13:
        raise Bad("missing IHDR: " + name)
    width, height, depth, colour, comp, filt, inter = struct.unpack(">IIBBBBB", ihdr)
    if (width, height) != (64, 64):
        raise Bad(f"{name}: size {width}x{height}, expected 64x64")
    if depth != 8:
        raise Bad(f"{name}: bit depth {depth}, expected 8")
    if colour != 6:
        raise Bad(f"{name}: colour type {colour}, expected 6 (RGBA)")
    if comp or filt or inter:
        raise Bad(f"{name}: unsupported PNG options")
    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    if len(raw) != height * (stride + 1):
        raise Bad(f"{name}: unexpected IDAT length")
    rows, prev, pos = [], bytearray(stride), 0
    for _ in range(height):
        kind, pos = raw[pos], pos + 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if kind > 4:
            raise Bad(f"{name}: unknown filter {kind}")
        for i in range(stride):
            left = line[i - 4] if i >= 4 else 0
            up = prev[i]
            if kind == 1:
                line[i] = (line[i] + left) & 255
            elif kind == 2:
                line[i] = (line[i] + up) & 255
            elif kind == 3:
                line[i] = (line[i] + ((left + up) >> 1)) & 255
            elif kind == 4:
                line[i] = (line[i] + paeth(left, up, prev[i - 4] if i >= 4 else 0)) & 255
        rows.append(line)
        prev = line
    return rows

def alpha(rows, x, y): return rows[y][x * 4 + 3]

def check_overlay(rows):
    for y in range(CY0, CY1):
        for x in range(CX0, CX1):
            if alpha(rows, x, y) != 0:
                raise Bad(f"overlay centre not transparent at ({x},{y}) alpha={alpha(rows, x, y)}")
    if not any(alpha(rows, x, y) == 255 for y in range(FY0, FY1) for x in range(FX0, FX1)):
        raise Bad("overlay has no opaque rim pixels on the plate face")

def special(node, base):
    return (
        isinstance(node, dict)
        and node.get("type") == "minecraft:special"
        and node.get("base") == base
        and isinstance(node.get("model"), dict)
        and node["model"].get("type") == "minemod:plated_shield"
    )

def check_item_model():
    if not ITEM.is_file():
        raise Bad("missing item model: " + ITEM.relative_to(ROOT).as_posix())
    try:
        model = json.loads(ITEM.read_text(encoding="utf-8"))["model"]
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise Bad(f"item model: bad JSON shape ({exc})")
    if model.get("type") != "minecraft:condition":
        raise Bad("item model: root type is not minecraft:condition")
    if model.get("property") != "minecraft:using_item":
        raise Bad("item model: property is not minecraft:using_item")
    if not special(model.get("on_false"), "minecraft:item/shield"):
        raise Bad("item model: on_false must be special base minecraft:item/shield with model.type minemod:plated_shield")
    if not special(model.get("on_true"), "minecraft:item/shield_blocking"):
        raise Bad("item model: on_true must be special base minecraft:item/shield_blocking with model.type minemod:plated_shield")

def check_java():
    if not CLIENT.is_file():
        raise Bad("missing client registration: " + CLIENT.relative_to(ROOT).as_posix())
    source = CLIENT.read_text(encoding="utf-8")
    if "minemod:plated_shield" not in source and not ('"minemod"' in source and '"plated_shield"' in source):
        raise Bad("client registration: codec id minemod:plated_shield not found")
    if "public static void register()" not in source:
        raise Bad("client registration: public static void register() not found")
    if not RENDERER.is_file():
        raise Bad("missing renderer: " + RENDERER.relative_to(ROOT).as_posix())
    source = RENDERER.read_text(encoding="utf-8")
    names = ('"entity/shield/plated_shield_base"', '"entity/shield/plated_shield_base_nopattern"', '"entity/shield/plated_shield_overlay"')
    for sprite in names:
        if sprite not in source:
            raise Bad("renderer: sprite reference " + sprite + " not found")
    # regression: the transparent overlay may never go through an opaque pipeline
    lines = source.splitlines()
    calls = [i for i, line in enumerate(lines) if "OVERLAY" in line and "submitModel" in line]
    if len(calls) != 2:
        raise Bad(f"renderer: {len(calls)} overlay submitModel calls, expected 2 (plain + glint)")
    for i in calls:
        if "entitySolid" in lines[i]:
            raise Bad(f"renderer: overlay uses a solid render type at line {i + 1}")
        if "Cutout" not in lines[i]:
            raise Bad(f"renderer: overlay render type at line {i + 1} is not a cutout type")
    glint = [i for i in calls if "Glint" in lines[i]]
    if len(glint) != 1 or "hasFoil" not in lines[glint[0] - 1]:
        raise Bad("renderer: overlay glint must be a hasFoil-only cutout branch")

def check_no_vanilla_assets():
    command = ["git", "status", "--porcelain", "--", "src/main/resources/assets/minecraft"]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise Bad("git status failed: " + result.stderr.strip())
    if result.stdout.strip():
        raise Bad("global vanilla asset changes: " + result.stdout.strip().replace("\n", "; "))

def write_png(path, rows):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw += row
    header = struct.pack(">IIBBBBB", len(rows[0]) // 4, len(rows), 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(SIG + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

def scaled(rows, factor):
    out = []
    for row in rows:
        wide = bytearray()
        for x in range(64):
            wide += row[x * 4:x * 4 + 4] * factor
        out.extend(bytearray(wide) for _ in range(factor))
    return out

def composited(base, overlay):
    out = []
    for y in range(64):
        line = bytearray()
        for x in range(64):
            br, bg, bb, ba = base[y][x * 4:x * 4 + 4]
            orr, og, ob, oa = overlay[y][x * 4:x * 4 + 4]
            total = oa + ba * (255 - oa) // 255
            if total == 0:
                line += bytes(4)
                continue
            line += bytes((
                (orr * oa + br * ba * (255 - oa) // 255) // total,
                (og * oa + bg * ba * (255 - oa) // 255) // total,
                (ob * oa + bb * ba * (255 - oa) // 255) // total,
                total,
            ))
        out.append(line)
    return out

def build_preview(textures):
    half = [scaled(textures[0], 4), scaled(textures[1], 4), scaled(textures[2], 4), scaled(composited(textures[0], textures[2]), 4)]
    rows = []
    for pair in (half[0:2], half[2:4]):
        for y in range(256):
            line = bytearray()
            for tile in pair:
                line += tile[y]
            rows.append(line)
    write_png(PREVIEW, rows)

def main():
    try:
        textures = [read_png(name) for name in SPRITES]
        for name, rows in zip(SPRITES, textures):
            if len({row[i] for row in rows for i in range(3, len(row), 4)}) < 2:
                raise Bad(f"{name}: alpha is constant")
        check_overlay(textures[2])
        check_item_model()
        check_java()
        check_no_vanilla_assets()
        build_preview(textures)
    except Bad as exc:
        fail(str(exc))
    except (OSError, zlib.error) as exc:
        fail("io error: " + str(exc))
    print("OK")

if __name__ == "__main__":
    main()
