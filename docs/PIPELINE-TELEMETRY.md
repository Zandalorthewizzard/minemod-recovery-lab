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
| `deleg_76dc5863` | 2026-11-19 | 6 | `deepseek-v4.1-flash` | 1153.6 с (19.23 мин) | 5090.7 с (84.85 мин) | ×4.413 | 196 | 407 | [`metrics/wave-deleg_76dc5863.json`](../metrics/wave-deleg_76dc5863.json) |


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


## Чего эти числа не доказывают
- Быстрая волна не означает исправленный продукт: скорость процесса и работоспособность механик — разные утверждения, они лежат в разных документах.
- wall включает ожидание ответов провайдера и загрузку зависимостей, а не только «полезную» работу.
- Прошлые зелёные прогоны автора в эти числа не входят и не наследуются.
- Замер без артефакта (лог, XML, отчёт) считается незавершённым, даже если время уже потрачено.
