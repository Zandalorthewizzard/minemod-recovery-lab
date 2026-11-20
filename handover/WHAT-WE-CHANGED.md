# Что мы изменили: разбор по git

Срез снят 2026-11-20T01:06:23+02:00 с ветки `intake/verified-export-0.1.0`
(HEAD `bb81b0110616b475d8fcc1b14d32e36b8079aeff`). Диапазон — `a863cb4..HEAD`,
где `a863cb4` — вершина `main` и одновременно `origin/main`. Тегов в репозитории нет.

Документ фиксирует только то, что видно в git: коммиты, пути, режимы файлов, содержимое
манифеста импорта. Оценок чужому коду здесь нет.

## 1. Сводка

| Показатель | Значение |
|---|---|
| Коммитов в ветке | 7 (`20eeb6d`, `c7f261c`, `3fab0a4`, `80fca77`, `dcc0657`, `12a42f2`, `bb81b01`) |
| Различных путей, затронутых коммитами | 104 |
| Из них отсутствует в дереве на HEAD | 4 (старый каталог `openspec/changes/verify-imported-baseline/`) |
| Итоговый дифф `a863cb4..HEAD` | 100 файлов: **99 добавлено, 1 изменён** (`README.md`), 0 удалено |
| Строк в итоговом диффе | +8751 / −30 |
| Сумма по коммитам | +8899 / −178 (больше итога: `README.md` и каталог `openspec/changes` переписывались внутри ветки) |
| `main` | не двигался: `a863cb4` = `origin/main` |
| Файлов в `main` до ветки | 3: `README.md`, `docs/checklist-next-cycle.md`, `docs/guide-bounded-reviews.md` |
| Из них не менялось | 2 (`docs/checklist-next-cycle.md`, `docs/guide-bounded-reviews.md`) |
| Импортированных продуктовых файлов | 56, все совпадают по SHA-256 с `provenance/source-manifest.json` |

Что **не** менялось вообще: `src/**` (46 файлов), `build.gradle`, `settings.gradle`,
`gradle.properties`, `gradle/wrapper/*`, `gradlew.bat`, лицензия и `authors` в
`src/main/resources/fabric.mod.json`, два документа лаборатории из `main`.

## 2. Этапы

| Этап | Коммиты | Файлов (уникальных) | Что появилось | Зачем |
|---|---|---|---|---|
| 1. Импорт аудированной базы и слой документов | `20eeb6d` | 75 (+4637/−5) | 56 файлов baseline без изменения байтов, `provenance/`, 6 документов, каркас `openspec/`, E0-метрики, `.gitignore`, переписанный `README.md` | зафиксировать исходную точку и границы доказательств |
| 2. Телеметрия и слой наблюдаемости | `c7f261c`, `3fab0a4`, `80fca77`, `dcc0657`, `bb81b01` | 21 (+3270/−1) | статус восстановления, журнал событий, замеры R1, четыре записи волн, read-only коллектор с self-test, два документа наблюдаемости | сделать путь до результата измеримым и проверяемым по файлам |
| 3. Карта ремонтов, архив change, спеки, передача | `12a42f2` | 18 (+992/−172) | `REPAIR-MAP.md`, `HANDOVER.md`, архив completed-change, два spec-файла, переписаны `README.md`/`BASELINE.md`/`PROJECT-MAP.md`/`DECISIONS.md`/`openspec/README.md` | один вход в проект и передача автору |

Пересечения: 10 путей затронуты и в этапе 1, и в этапе 3 — `README.md`, `docs/BASELINE.md`,
`docs/DECISIONS.md`, `docs/DELIVERY.md`, `docs/PROJECT-MAP.md`, `openspec/README.md` и четыре
файла старого change-каталога, которые в этапе 3 удалены. Между этапами 2 и 3 пересечений нет.

### Этап 1 — `20eeb6d` «chore: import audited baseline and incremental OpenSpec layer»

| Группа | Кол-во | Состав |
|---|---|---|
| Код (импорт) | 29 | `src/main/java` (14), `src/client/java` (7), `src/gametest/java` (8) |
| Ресурсы и данные (импорт) | 17 | `src/main/resources` (16): `fabric.mod.json`, `assets/minemod/{items,models,lang,textures}`, `data/minemod/{loot_table,recipe}`; плюс `src/gametest/resources/fabric.mod.json` (1) |
| Сборка (импорт) | 7 | `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` |
| Генераторы (импорт) | 3 | `tools/gen_textures.py`, `tools/art/plated_shield/gen_plated_shield.py`, `tools/art/plated_shield/check_plated_shield.py` |
| Происхождение | 3 | `provenance/README.md`, `source-manifest.json` (56 записей с SHA-256), `intake-checks.json` |
| Документация | 6 | `docs/BASELINE.md`, `SECURITY.md`, `DECISIONS.md`, `DELIVERY.md`, `PROJECT-MAP.md`, `E0-SCORECARD.md` |
| Метрики E0 | 2 | `metrics/e0-wave.json`, `metrics/e0-events.jsonl` |
| OpenSpec | 6 | `openspec/README.md`, `openspec/config.yaml`, `changes/verify-imported-baseline/{.openspec.yaml,proposal.md,design.md,tasks.md}` |
| Корень | 2 | новый `.gitignore`, изменённый `README.md` |

Итого 75 = 56 импортированных + 19 наших файлов слоя лаборатории.

### Этап 2 — телеметрия и наблюдаемость

| Коммит | Файлов | Что добавил |
|---|---|---|
| `c7f261c` | 7 (+685) | `docs/RECOVERY-STATUS.md`, `docs/PIPELINE-TELEMETRY.md`, `metrics/events.jsonl` (журнал), `metrics/r1-gametests.json`, `metrics/r1-runtime.json`, две записи волн `wave-deleg_4924890d.json`, `wave-deleg_76dc5863.json` |
| `3fab0a4` | 2 (+2) | по одной строке в `docs/PIPELINE-TELEMETRY.md` и `metrics/events.jsonl` — событие публикации R1 |
| `80fca77` | 15 (+2319) | `docs/observability/README.md` (правила слоя), `docs/observability/R1-SCORECARD.md`, срез прогона `metrics/r1-20261119T212411Z/` (7 файлов: `commands.txt`, `determinism.txt`, `events.jsonl`, `provenance.json`, `run.json`, `selftest-synthetic.txt`, `workers.json`), `wave-deleg_895b10e1.json`, `tools/observability/collect_run.py` (911 строк) и `test_collect_run.py` (419 строк), правки статуса и телеметрии |
| `dcc0657` | 2 (+82/−1) | `docs/PIPELINE-COMPARISON.md` (сравнение пайплайнов по замерам), раздел в `RECOVERY-STATUS.md` |
| `bb81b01` | 3 (+182) | `metrics/wave-deleg_3b8f0c4e.json` (волна документации), 19 строк в `docs/PIPELINE-TELEMETRY.md` и три строки-события в `metrics/events.jsonl` |

Группы: документация 5 путей (`PIPELINE-TELEMETRY`, `RECOVERY-STATUS`, `PIPELINE-COMPARISON`,
`observability/README`, `observability/R1-SCORECARD`), метрики/данные 14 путей
(`metrics/events.jsonl`, `r1-gametests.json`, `r1-runtime.json`, каталог среза из 7 файлов,
4 записи волн), инструменты 2 пути (`tools/observability/*`).

`tools/observability/*` — единственный исполняемый код, добавленный после импорта: сборщик
среза волны и его self-test. Продуктовых путей в этом этапе нет.

### Этап 3 — `12a42f2` «docs: repair map, archived verification change, handover and one entry point»

| Группа | Кол-во | Состав |
|---|---|---|
| Новая документация | 2 | `docs/REPAIR-MAP.md` (188 строк, чанки A–K, полосы L1–L4), `docs/HANDOVER.md` (170 строк) |
| Переписано | 5 | `README.md` (единая точка входа), `docs/BASELINE.md`, `docs/PROJECT-MAP.md`, `docs/DECISIONS.md` (LAB-06…LAB-12), `docs/DELIVERY.md` (ссылка на архив) |
| OpenSpec: архив | 4 добавлено / 4 удалено | `openspec/changes/archive/2026-11-19-verify-imported-baseline/{.openspec.yaml,proposal.md,design.md,tasks.md}` вместо `openspec/changes/verify-imported-baseline/…` |
| OpenSpec: спеки | 2 | `openspec/specs/shellback-encounter/spec.md`, `openspec/specs/plated-shield/spec.md` |
| OpenSpec: индекс | 1 | `openspec/README.md` |

Перемещение change-каталога в `archive/` — единственное удаление путей в ветке; содержимое
не потеряно, те же четыре файла лежат по новому пути.

## 3. Исходный код и данные проекта

Ни один коммит после импорта не касается продуктовых путей. Проверяется одной командой:
`git log --oneline 20eeb6d..HEAD -- src build.gradle settings.gradle gradle.properties gradle gradlew gradlew.bat`
не даёт вывода, а `git log --oneline -- src …` возвращает ровно `20eeb6d`.

Не правились вообще (46 файлов `src/**`, режимы и содержимое):

| Путь | Что это | Коммитов, меняющих файл |
|---|---|---|
| `src/main/java/dev/minemod/**` | 14 файлов: мод, сущность `Shellback*`, парирование, предметы, рецепты, спавн, компоненты, API | 1 (только импорт) |
| `src/client/java/dev/minemod/client/**` | 7 файлов: клиент, рендерер моба, специальный рендерер щита, геометрия, тултипы | 1 |
| `src/gametest/java/dev/minemod/test/**` | 8 файлов: 6 GameTest-классов, клиентский smoke, поддержка парирования | 1 |
| `src/main/resources/**` | 16 файлов: `fabric.mod.json`, `assets/minemod/{items,models,lang,textures}`, `data/minemod/{loot_table,recipe}` | 1 |
| `src/gametest/resources/fabric.mod.json` | манифест тестового источника | 1 |
| `build.gradle`, `settings.gradle`, `gradle.properties` | сборка | 1 |
| `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`, `gradlew.bat` | wrapper | 1 |
| `tools/gen_textures.py`, `tools/art/plated_shield/*` | генераторы арта | 1 |

Единственное изменение в импорте — **бит исполнения `gradlew`**: файл добавлен в git с режимом
`100755` (Unix), при этом содержимое не менялось. Это утверждение проверяемо: SHA-256 рабочей
копии `gradlew` = `a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59`,
ровно как в `provenance/source-manifest.json`, а `git ls-tree HEAD gradlew` показывает `100755`.
`gradlew.bat` остался `100644` (CRLF сохранены). `core.filemode` в этом клоне выключен, поэтому
режим — свойство индекса, а не файловой системы.

Все 56 файлов из манифеста совпадают с рабочим деревом побайтово: 56 из 56, расхождений и
отсутствующих нет (проверено sha256 по каждой записи манифеста).

## 4. Что изменено в метаданных и интеграции

| Объект | Факт |
|---|---|
| Точка импорта | `provenance/source-manifest.json`: `upstream_commit` = `f69785bbe15088b0d015c71d8cf187aa365c5079`, `upstream_tree_matches_export: true`, `runtime_executed: false`, `license_preserved: "All-Rights-Reserved"` |
| Состав импорта | 56 записей `path`/`bytes`/`sha256`; импортирована рабочая копия одного коммита, upstream-история не переносилась |
| Что не копировалось из upstream | старый `README`, три upstream-документа, upstream `.gitignore` — вместо них файлы лаборатории (`provenance/README.md`) |
| Атрибуция и лицензия | `All-Rights-Reserved` и `authors: []` в `src/main/resources/fabric.mod.json` не менялись (файл входит в 56 совпавших) |
| `.gitignore` | новый, 15 строк: `.gradle/`, `.gradle-user-home/`, `build/`, `run/`, `evidence/`, `out/`, `.idea/`, `*.iml`, `.DS_Store`, `.env`, `.env.*`, `!.env.example`, `*.log`, `__pycache__/`, `*.pyc` |
| `README.md` | единственный изменённый файл относительно `main`: +56/−30, переписан под лабораторию и стал единой точкой входа (второй раз — в `12a42f2`) |
| OpenSpec | `openspec/config.yaml` + `openspec/README.md` — постепенный слой; на HEAD один change в `archive/` и два spec-файла |
| Метрики | машинные записи в `metrics/`: `events.jsonl` (журнал), срез `r1-20261119T212411Z/`, 4 файла `wave-deleg_*.json`, E0-файлы |

Ничего из перечисленного не меняет поведение мода: это индекс, правила исключений, манифест
происхождения и слой договорённостей.

## 5. Как проверить, что мы ничего не сломали

Все команды read-only, выполняются из корня репозитория.

1. **Продуктовые пути не тронуты после импорта**
   ```
   git log --oneline 20eeb6d..HEAD -- src build.gradle settings.gradle gradle.properties gradle gradlew gradlew.bat
   ```
   Должно быть **пусто**. Любая строка здесь означает правку кода после импорта.

2. **Дифф к `main` — только добавления и один README**
   ```
   git diff --name-status --no-renames a863cb4..HEAD | awk '{print $1}' | sort | uniq -c
   git diff --stat --no-renames a863cb4..HEAD | tail -1
   ```
   Ожидается `99 A` и `1 M`, итог — `100 files changed, 8751 insertions(+), 30 deletions(-)`.

3. **Импорт побайтово совпадает с манифестом**
   ```
   python -c "import json,hashlib;m=json.load(open('provenance/source-manifest.json'));print(sum(hashlib.sha256(open(f['path'],'rb').read()).hexdigest()==f['sha256'] for f in m['files']),'/',len(m['files']))"
   ```
   Ожидается `56 / 56`. Меньшее число — изменённый или отсутствующий файл baseline.

4. **`gradlew` отличается только режимом, и рабочее дерево чистое**
   ```
   git ls-tree HEAD gradlew gradlew.bat
   sha256sum gradlew
   git status --short
   ```
   Ожидается `100755` у `gradlew`, `100644` у `gradlew.bat`, хеш
   `a5a5c199…a3f72d59`, пустой `git status`.

## 6. Ограничения этого документа

- Git показывает пути, байты и режимы, но не смысл. Утверждения внутри `metrics/*.json`,
  `docs/RECOVERY-STATUS.md`, `docs/observability/R1-SCORECARD.md` — это записи, а не проверки:
  сам документ их не подтверждает и не опровергает.
- Прогоны (сборка, серверные GameTests, клиентский запуск) выполнялись вне этого дерева и не
  воспроизводимы по git; в репозитории остались только их текстовые и JSON-записи. Повторить
  прогон — отдельная задача, этот документ её не выполнял.
- Сырые материалы вне репозитория не проверяемы по определению: upstream-история, исходный
  bundle, сырые планы, переписка, логи, исторические worktree, готовые JAR мода. Приватные
  отчёты волн, на которые ссылаются записи в `metrics/`, в репозитории отсутствуют.
- Проверка «файл не менялся» опирается на `git log` по пути и на манифест с SHA-256. Если файл
  был изменён и возвращён к исходным байтам внутри ветки, дифф этого не покажет; для 56
  импортированных файлов такая правка исключена только совпадением хешей, а не историей.
- Диапазон ограничен `a863cb4..bb81b01` в ветке `intake/verified-export-0.1.0`. Что происходит
  в `main` после `a863cb4`, здесь не описано: на момент среза `main` и `origin/main` совпадали
  с базой.
- Арт и текстуры подтверждены как файлы (наличие, размер, хеш), но не как изображение —
  визуальная приёмка в этом разборе не проводилась.
