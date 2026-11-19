# Индекс пакета передачи

Ветка `intake/verified-export-0.1.0`, снимок на HEAD `bb81b01` (последующие документальные
коммиты код и данные не меняют). В папке три документа: этот индекс, разбор изменений и разбор
найденного; сама документация не скопирована, а адресована.

## 1. Куда смотреть с каким вопросом

| Документ | На какой вопрос отвечает | Доказательство рядом с ним |
|---|---|---|
| [../docs/HANDOVER.md](../docs/HANDOVER.md) | Что проверено, чего нет, как запускать, что дальше по порядку | ссылки на `metrics/r1-*`, `provenance/source-manifest.json` |
| [../docs/REPAIR-MAP.md](../docs/REPAIR-MAP.md) | Из каких наблюдаемых чанков A–K состоит ремонт, порядок, параллелизм | зоны записи — файлы с номерами строк |
| [../docs/RECOVERY-STATUS.md](../docs/RECOVERY-STATUS.md) | Что работает, что только в тесте, что не подключено, что требует рук | матрица «сценарий → статус → чем подтверждено» |
| [../docs/PIPELINE-COMPARISON.md](../docs/PIPELINE-COMPARISON.md) | Почему выходило «сделано, но не работает» — по замерам | `metrics/wave-*.json`, `metrics/events.jsonl` |
| [../docs/PIPELINE-TELEMETRY.md](../docs/PIPELINE-TELEMETRY.md) | Сколько заняли волны и чем это записано | те же машинные записи, ручных значений нет |
| [../README.md](../README.md) | Статус в шесть строк и навигация по всем документам | `metrics/r1-runtime.json` |
| [../openspec/README.md](../openspec/README.md) | Как применяется OpenSpec: спека — принятое поведение, не план | архив change: 11 задач закрыты, 3 нет |
| [../metrics/r1-runtime.json](../metrics/r1-runtime.json) | Числа прогона R1 и список `not_verified` | сам файл: команды, секунды, SHA-256, окружение |
| [../metrics/r1-gametests.json](../metrics/r1-gametests.json) | Поимённый состав 26 кейсов | массив `cases`, 26 записей |
| [../provenance/source-manifest.json](../provenance/source-manifest.json) | 56 импортированных файлов и их SHA-256 | манифест; байты исходников не менялись |

## 2. Проверить за 15 минут

Из корня репозитория, Git Bash. Числа R1 сняты на `20eeb6d8`, после него — только коммиты документации.

**Шаг 1 (2 мин) — ревизия и toolchain**
```bash
git rev-parse --abbrev-ref HEAD            # intake/verified-export-0.1.0
git log --oneline -1                       # bb81b01
git diff --stat 20eeb6d8..HEAD -- src build.gradle gradle.properties settings.gradle
java -version                              # нужен 25.x (в прогоне R1: Temurin 25.0.5+8)
echo "$JAVA_HOME"                          # должен указывать на этот JDK
```
Пустой вывод `git diff` = код на HEAD тот же, что в прогоне R1; непустой = числа R1 про другой код.
JDK не скачивается сам (`org.gradle.java.installations.auto-download=false`): при JDK ниже 25 сборка упадёт — это ожидаемо.

**Шаг 2 (3 мин, первая сборка дольше) — сборка и серверные тесты**
```bash
export GRADLE_USER_HOME="$PWD/.gradle-user-home"   # кэш держите вне checkout
./gradlew --no-daemon clean build
```
Ожидается `BUILD SUCCESSFUL` и JAR `build/libs/minemod-0.1.0.jar`. 231 с — время нашей машины; на
первой сборке Loom скачивает зависимости, поэтому дольше. `clean build` сам выполняет `:runGameTest`.

**Шаг 3 (2 мин) — 26 = 25 модовых + ванильная заглушка, считать из XML**
```bash
XML=build/test-results/gameTest/game-test.xml
grep -c '<testcase' $XML                          # 26
grep -c 'name="minemod-test:' $XML                # 25
grep -c '<failure' $XML                           # 0
grep -rho '@GameTest' src/gametest/java | wc -l   # 25
```
Последняя команда связывает XML с исходниками: 25 сценариев `minemod-test:` лежат в
`src/gametest/java`, 26-й — `minecraft:always_pass`. Консольная сводка эти два вида не различает,
поэтому число берётся из XML. Другие числа = сверьтесь с `metrics/r1-gametests.json` и назовите расхождение по имени кейса.

**Шаг 4 (5 мин) — клиентский прогон, отдельный от серверного**
```bash
./gradlew --no-daemon runClientGameTest
ls src/main/resources/data/minemod   # loot_table recipe — каталога advancement нет
```
Нужен дисплей и GL. Ожидается `BUILD SUCCESSFUL` (~250 с) и строка `Summoned new Shellback`: клиентский
source set загружается, entrypoint не падает, мир поднимается, команда принята. Вид моба, иконок и щита
этим не проверяется (`not_verified` в `metrics/r1-runtime.json`); без дисплея результат — «не проверено», а не PASS.

## 3. Утверждение → доказательство → где лежит

| Утверждение | Чем подтверждено | Где лежит |
|---|---|---|
| Сборка успешна за 231 с | `build.seconds` в `metrics/r1-runtime.json`; событие `build_passed` | `docs/RECOVERY-STATUS.md` |
| 26 кейсов, 0 падений, 5.024 с | `tests_total`, `failed`, `suite_complete_seconds` там же | там же |
| 26 = 25 модовых + `always_pass` | 25 имён `minemod-test:` в `cases[]`; 25 `@GameTest` в `src/gametest/java` | `docs/HANDOVER.md` §2 |
| Клиентский прогон 250 с | `client_gametest.seconds` и `observed[]` в `metrics/r1-runtime.json` | `docs/RECOVERY-STATUS.md` |
| Второй прогон: 1 м 47 с, снова 26/26 | событие `wave_runner_completed`; `metrics/wave-deleg_895b10e1.json` | `docs/HANDOVER.md` §2 |
| Схема лут-таблицы валидна для 26.3 | там же: «condition/modifier valid for 26.3»; JSON не правился | `docs/REPAIR-MAP.md`, «Что не входит» |
| Визуал моба не подключён | `ShellbackRenderer` берёт ванильную модель, своя геометрия не запекается | `docs/RECOVERY-STATUS.md`, чанк G |
| Иконки пластины и яйца ванильные | `models/item/*.json` → `minecraft:item/armadillo_scute`, `…/silverfish_spawn_egg` | `docs/REPAIR-MAP.md`, чанк H |
| В креативе моб не выбирается целью | селектор целей: игрок в креативе/наблюдателе не цель — стоит, без замаха и рывка | `docs/REPAIR-MAP.md`, чанк B |
| Окно парирования короче замаха | окно `[5,11]` при замахе 16 тиков, без нижней границы | `docs/REPAIR-MAP.md`, чанк E |
| Advancement отсутствует | в `data/minemod/` только `recipe` и `loot_table`; в книге рецептов его нет | `docs/REPAIR-MAP.md`, чанк J |
| Спавн узкий, охват биомов не доказан | гейт ниже 50 блоков без неба и при свете; изменён 1 биом из 67, тест — одна позиция | `docs/REPAIR-MAP.md`, чанк K |

Визуал моба и иконки — «не подключён» / «чужие текстуры», поведение в бою и спавн — «требует проверки руками»: это чтение кода и тики стенда, не игра живым игроком.

## 4. Чего здесь нет

- **Готового релиза:** ни тега, ни опубликованного JAR — JAR из `build/` это артефакт прогона; `origin/main` на `a863cb4`.
- **CI:** любое «зелёно» здесь — локальный прогон с указанной ревизией, JDK и Gradle.
- **Гарантий безопасности зависимостей:** `gradle/verification-metadata.xml` отсутствует, транзитивные артефакты не проверялись.
- **Обещаний сроков:** бюджеты процедурные, таймаутов нет; два агента из шести вышли за лимит вызовов (`docs/PIPELINE-COMPARISON.md`).
- **Проверки ощущений:** цикл «моб → бой → пластина → крафт → щит» в выживании не проходил никто, пиксельный вид не проверен.
