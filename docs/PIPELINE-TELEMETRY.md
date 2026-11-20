# Pipeline telemetry
Реальные замеры работы над восстановлением проекта. Документ **не** описывает механики мода — только время, исполнителей и стоимость пути. Все числа получены из машинных записей: `metrics/*.json` (волны) и `metrics/events.jsonl` (события). Ни одно значение здесь не набрано вручную.

Зачем это нужно: авторский пайплайн оставил после себя код, который «сделан, но не работает», и не оставил наблюдаемости — нельзя ответить, сколько занял этап и что именно было доказано. Здесь на каждый шаг есть время, исполнитель и артефакт.


## Как читать числа
- **wall** — сколько реально прошло от запуска волны до её завершения.
- **сумма интервалов** — сумма собственного времени каждого агента. При параллельной работе она **больше** wall; это не затраченное время, а объём работы.
- **параллельность** — сумма интервалов / wall. Показывает, сколько работы реально шло одновременно; 1.0 означает «всё последовательно».
- **API / tool calls** — фактическое число обращений, а не оценка.
- Стоимость: если провайдер не отдал значение, стоит `null`, а не ноль.

## Волны
| волна | дата | агентов | модель | wall | сумма интервалов | параллельность | API | tool calls | запись |
|---|---|---|---|---|---|---|---|---|---|
| `deleg_4924890d` | 2026-11-19 | 6 | `deepseek-v4.1-flash` | 577.9 с (9.63 мин) | 2824.3 с (47.07 мин) | ×4.888 | 151 | 157 | [`metrics/wave-deleg_4924890d.json`](../metrics/wave-deleg_4924890d.json) |
| `deleg_895b10e1` | 2026-11-19 | 6 | `deepseek-v4.1-flash` | 950.8 с (15.85 мин) | 3754.6 с (62.58 мин) | ×3.949 | 174 | 183 | [`metrics/wave-deleg_895b10e1.json`](../metrics/wave-deleg_895b10e1.json) |
| `deleg_76dc5863` | 2026-11-19 | 6 | `deepseek-v4.1-flash` | 1153.6 с (19.23 мин) | 5090.7 с (84.85 мин) | ×4.413 | 196 | 407 | [`metrics/wave-deleg_76dc5863.json`](../metrics/wave-deleg_76dc5863.json) |
| `deleg_3b8f0c4e` | 2026-11-20 | 6 | `deepseek-v4.1-flash` | 611.7 с (10.20 мин) | 2286.8 с (38.11 мин) | ×3.738 | 177 | 211 | [`metrics/wave-deleg_3b8f0c4e.json`](../metrics/wave-deleg_3b8f0c4e.json) |
| `deleg_3abcb636` | 2026-11-20 | 3 | `deepseek-v4.1-flash` | 490.5 с (8.17 мин) | 1349.9 с (22.50 мин) | ×2.752 | 100 | 112 | [`metrics/wave-deleg_3abcb636.json`](../metrics/wave-deleg_3abcb636.json) |
| `deleg_c6e4c4f1` | 2026-11-20 | 1 | `deepseek-v4.1-flash` | 623.7 с (10.40 мин) | 623.5 с (10.39 мин) | ×1.0 | 52 | 63 | [`metrics/wave-deleg_c6e4c4f1.json`](../metrics/wave-deleg_c6e4c4f1.json) |
| `deleg_01223d4a` | 2026-11-20 | 1 | `deepseek-v4.1-flash` | 1390.9 с (23.18 мин) | 1390.4 с (23.17 мин) | ×1.0 | 76 | 79 | [`metrics/wave-deleg_01223d4a.json`](../metrics/wave-deleg_01223d4a.json) |
| `deleg_55e25c17` | 2026-11-20 | 1 | `deepseek-v4.1-flash` | 1008.5 с (16.81 мин) | 1008.1 с (16.80 мин) | ×1.0 | 43 | 52 | [`metrics/wave-deleg_55e25c17.json`](../metrics/wave-deleg_55e25c17.json) |
| `deleg_5f153e1d` | 2026-11-20 | 1 | `deepseek-v4.1-flash` | 1066.1 с (17.77 мин) | 1065.7 с (17.76 мин) | ×1.0 | 55 | 63 | [`metrics/wave-deleg_5f153e1d.json`](../metrics/wave-deleg_5f153e1d.json) |


### Волна `deleg_4924890d` — по агентам
Запуск 2026-11-19T22:24:38+02:00 → завершение 2026-11-19T22:34:16+02:00. wall 577.9 с (9.63 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | build & supply chain (E0) | completed | 473.4 | ×0.82 | 28 | 31 |
| 1 | java code security (E0) | completed | 261.3 | ×0.45 | 14 | 21 |
| 2 | scripts & publication (E0) | completed | 424.87 | ×0.74 | 23 | 22 |
| 3 | documentation authority (E0) | completed | 577.06 | ×1.00 | 35 | 34 |
| 4 | features & tests (E0) | completed | 557.97 | ×0.97 | 25 | 24 |
| 5 | delivery pipeline (E0) | completed | 529.72 | ×0.92 | 26 | 25 |

Токены: вход 7794797, выход 131913. Стоимость: null.


### Волна `deleg_895b10e1` — по агентам
Запуск 2026-11-19T23:27:39+02:00 → завершение 2026-11-19T23:43:29+02:00. wall 950.8 с (15.85 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | runtime runner: portable JDK 25, isolated env, clean build, server GameTests | completed | 950.26 | ×1.00 | 41 | 47 |
| 1 | combat & spawn reproductions (static) | completed | 400.95 | ×0.42 | 14 | 13 |
| 2 | loot, craft, component preservation (static) | completed | 490.2 | ×0.52 | 26 | 25 |
| 3 | parry & client path (static) | completed | 431.06 | ×0.45 | 23 | 22 |
| 4 | author pipeline evidence (static) | completed | 669.51 | ×0.70 | 33 | 35 |
| 5 | observability layer: collector + self-test | completed | 812.64 | ×0.85 | 37 | 41 |

Токены: вход 9236095, выход 185084. Стоимость: null.


### Волна `deleg_76dc5863` — по агентам
Запуск 2026-11-19T23:35:43+02:00 → завершение 2026-11-19T23:54:56+02:00. wall 1153.6 с (19.23 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | integration & registration (r1/reports/01) | completed | 662.99 | ×0.57 | 29 | 66 |
| 1 | entity & AI (r1/reports/02) | completed | 1153.19 | ×1.00 | 21 | 61 |
| 2 | loot & craft chain (r1/reports/03) | completed | 695.73 | ×0.60 | 26 | 70 |
| 3 | parry & shield (r1/reports/04) | completed | 848.45 | ×0.74 | 23 | 56 |
| 4 | tests & coverage (r1/reports/05) | completed | 859.09 | ×0.74 | 34 | 60 |
| 5 | author pipeline forensics (r1/reports/06) | completed | 871.26 | ×0.76 | 63 | 94 |

Токены: вход 15126198, выход 266373. Стоимость: null.


### Волна `deleg_3b8f0c4e` — по агентам
Запуск 2026-11-20T00:29:58+02:00 → завершение 2026-11-20T00:40:09+02:00. wall 611.7 с (10.20 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | repair map (docs/REPAIR-MAP.md) | completed | 457.77 | ×0.75 | 21 | 28 |
| 1 | openspec archive and specs | completed | 611.26 | ×1.00 | 43 | 65 |
| 2 | handover document | completed | 474.92 | ×0.78 | 44 | 43 |
| 3 | single entry point: README and PROJECT-MAP | completed | 355.31 | ×0.58 | 37 | 41 |
| 4 | decisions LAB-06..LAB-12 | completed | 150.07 | ×0.25 | 16 | 19 |
| 5 | baseline after R1 | completed | 237.48 | ×0.39 | 16 | 15 |

Токены: вход 10343767, выход 128329. Стоимость: null.


### Волна `deleg_3abcb636` — по агентам
Запуск 2026-11-20T01:06:14+02:00 → завершение 2026-11-20T01:14:24+02:00. wall 490.5 с (8.17 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | handover index and 15-minute verification | completed | 490.0 | ×1.00 | 32 | 39 |
| 1 | what we changed (git account) | completed | 389.93 | ×0.79 | 33 | 39 |
| 2 | what we found (process forensics) | completed | 469.97 | ×0.96 | 35 | 34 |

Токены: вход 5626619, выход 87130. Стоимость: null.


### Волна `deleg_c6e4c4f1` — по агентам
Запуск 2026-11-20T01:18:28+02:00 → завершение 2026-11-20T01:28:52+02:00. wall 623.7 с (10.40 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | chunk A: acceptance protocol | completed | 623.55 | ×1.00 | 52 | 63 |

Токены: вход 4022048, выход 44454. Стоимость: null.


### Волна `deleg_01223d4a` — по агентам
Запуск 2026-11-20T01:27:16+02:00 → завершение 2026-11-20T01:50:27+02:00. wall 1390.9 с (23.18 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | chunk B: creative targeting (executor) | completed | 1390.36 | ×1.00 | 76 | 79 |

Токены: вход 6257168, выход 48808. Стоимость: null.


### Волна `deleg_55e25c17` — по агентам
Запуск 2026-11-20T02:07:50+02:00 → завершение 2026-11-20T02:24:39+02:00. wall 1008.5 с (16.81 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | chunk C: retaliation after deflect (executor) | completed | 1008.11 | ×1.00 | 43 | 52 |

Токены: вход 3098185, выход 49627. Стоимость: null.


### Волна `deleg_5f153e1d` — по агентам
Запуск 2026-11-20T02:29:30+02:00 → завершение 2026-11-20T02:47:16+02:00. wall 1066.1 с (17.77 мин).

| # | роль | статус | интервал, с | доля wall | API | tool calls |
|---|---|---|---|---|---|---|
| 0 | chunk D: damage-type gate (executor) | completed | 1065.66 | ×1.00 | 55 | 63 |

Токены: вход 3597116, выход 48302. Стоимость: null.


## Журнал событий
Каждая строка — реальное машинное время с часовым поясом. Полный лог: [`metrics/events.jsonl`](../metrics/events.jsonl).

| время | событие | исполнитель | что произошло |
|---|---|---|---|
| 2026-11-19T23:37:02+02:00 | `wave_dispatched` | parent | R1 diagnostics wave: 6 read-only scouts dispatched (integration, entity/AI, loot/craft, parry/shield, tests, author pipeline) |
| 2026-11-19T23:37:02+02:00 | `env_jdk` | parent | JDK 25.0.5+8 download started to <local toolchain dir>(no JDK on PATH before this) |
| 2026-11-19T23:38:36+02:00 | `env_jdk_ready` | parent | JDK 25.0.5+8 verified: java/javac respond, Temurin build 25.0.5+8-LTS |
| 2026-11-19T23:38:37+02:00 | `build_started` | parent | gradlew --no-daemon clean build in isolated worktree (JAVA_HOME=jdk25, isolated GRADLE_USER_HOME, Gradle 9.7.0) |
| 2026-11-19T23:44:13+02:00 | `build_passed` | parent | clean build succeeded in 3m51s; 26 GameTests COMPLETE IN 5.024 s, All 26 required tests passed (our fresh run, isolated worktree) |
| 2026-11-19T23:44:56+02:00 | `client_gametest_started` | parent | runClientGameTest launched in isolated worktree: exercises client source set and ClientSmokeTest with a real GL context |
| 2026-11-19T23:51:43+02:00 | `client_gametest_passed` | parent | runClientGameTest BUILD SUCCESSFUL in 4m10s (exit 0); client booted, world loaded, /summon Shellback accepted, shield given to player, no recipe/loot parse errors in log |
| 2026-11-19T23:52:46+02:00 | `build_verified_tests` | parent | XML parsed: 26 testcases, 0 failures, 25 mod tests + 1 vanilla always_pass; suite 5.024s |
| 2026-11-19T23:56:21+02:00 | `wave_completed` | parent | R1 diagnostics wave finished: 6/6 completed, wall 1153.58 s (19.23 min), sum of child intervals 5090.71 s, parallel ratio x4.41, 196 API / 407 tool calls |
| 2026-11-20T00:00:08+02:00 | `docs_rendered` | parent | observability layer published: docs/PIPELINE-TELEMETRY.md + metrics/wave-*.json + sanitized events.jsonl, generated by r1_collect.py/r1_render_docs.py from state.db records |
| 2026-11-20T00:01:20+02:00 | `r1_published` | parent | R1 layer pushed to intake/verified-export-0.1.0: commit c7f261c, remote readback 0/0 divergence, PR #1 updated (82 files, still draft) |
| 2026-11-20T00:04:10+02:00 | `wave_runner_completed` | parent | runtime-runner wave deleg_895b10e1 finished: 6/6, wall 950.8 s, sum 3754.62 s (x3.95), 174 API / 183 tool calls; independent build 1m47s and 26/26 GameTests; loot schema verdict: condition/modifier valid for 26.3 |
| 2026-11-20T00:30:18+02:00 | `writers_wave_dispatched` | parent | documentation wave: 6 writers with disjoint file allowlists (repair map, openspec archive+specs, handover, README/PROJECT-MAP, DECISIONS, BASELINE) |
| 2026-11-20T01:01:19+02:00 | `docs_wave_completed` | parent | documentation wave finished: 6/6 writers, wall 611.71 s (10.2 min), sum 2286.81 s (x3.74), 177 API / 211 tool calls; verify_docs PASS (0 outside allowlist, 0 broken links, 0 leaks) |
| 2026-11-20T01:01:20+02:00 | `docs_wave_published` | parent | commit 12a42f2: repair map, archived change, two specs, handover, single entry point, LAB-06..12, baseline update |
| 2026-11-20T01:16:04+02:00 | `handover_published` | parent | handover package committed 78ebe5d: 3 documents, verify PASS (0 outside allowlist, 0 broken links, 0 leaks), 103 files in PR #1 |
| 2026-11-20T01:54:34+02:00 | `chunk_b_accepted` | parent | chunk B accepted: RED test failed (target=null on tick 20), after fix build 52s, 27/27 GameTests (26 mod + 1 vanilla), client run 65s exit 0; commit 52e70fc |
| 2026-11-20T02:28:40+02:00 | `chunk_c_accepted` | parent | chunk C accepted: RED lastHurtByMob=null on tick 2, after fix build 57s, 28/28 (27 mod + 1 vanilla), client 62s exit 0; commit b1db57c |
| 2026-11-20T02:53:02+02:00 | `chunk_d_accepted` | parent | chunk D accepted: RED blast 24.0->24.0, after fix 24.0->0.0; 3 clean builds 29/29 (28 mod + 1 vanilla), client 53s exit 0; commit dfa83df; repair series closed before handover |


## Чего эти числа не доказывают
- Быстрая волна не означает исправленный продукт: скорость процесса и работоспособность механик — разные утверждения, они лежат в разных документах.
- wall включает ожидание ответов провайдера и загрузку зависимостей, а не только «полезную» работу.
- Прошлые зелёные прогоны автора в эти числа не входят и не наследуются.
- Замер без артефакта (лог, XML, отчёт) считается незавершённым, даже если время уже потрачено.


## Как это воспроизвести

- Коллектор среза волны: [`tools/observability/collect_run.py`](../tools/observability/collect_run.py) — read-only чтение `state.db`, без сети и опроса; рядом self-test [`tools/observability/test_collect_run.py`](../tools/observability/test_collect_run.py).
- Методология и разделение величин (elapsed от GO, оркестрация до dispatch, wall, сумма и объединение интервалов): [`docs/observability/README.md`](observability/README.md).
- Сырой срез последней волны: [`metrics/r1-20261119T212411Z/run.json`](../metrics/r1-20261119T212411Z/run.json).
- Таблицы этого документа собраны тем же способом из того же `state.db`; ручных значений нет.
