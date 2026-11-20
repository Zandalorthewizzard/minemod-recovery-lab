# Индекс пакета передачи

Ветка `intake/verified-export-0.1.0`, HEAD `93073fa` (draft PR #1, `origin/main` не тронут —
`a863cb4`). В папке четыре документа: этот индекс, что изменено, что найдено и **что уже
отремонтировано**; остальная документация не скопирована, а адресована.

От базовой ревизии `20eeb6d8` код менялся ровно в двух файлах — это и есть вся наша правка
продукта:

```text
src/main/java/dev/minemod/entity/ShellbackEntity.java          +93 −2
src/gametest/java/dev/minemod/test/ShellbackCombatGameTest.java +203 −0
```

## 1. Куда смотреть с каким вопросом

| Документ | На какой вопрос отвечает | Доказательство рядом с ним |
|---|---|---|
| [../docs/HANDOVER.md](../docs/HANDOVER.md) | Что проверено, чего нет, как запускать, что дальше по порядку | ссылки на `metrics/r1-*`, `provenance/source-manifest.json` |
| [WHAT-WE-REPAIRED.md](WHAT-WE-REPAIRED.md) | Что именно отремонтировано, чем доказано и сколько заняло | коммиты `52e70fc`, `b1db57c`, `dfa83df`; `metrics/chunk-{b,c,d}.json` |
| [../docs/REPAIR-MAP.md](../docs/REPAIR-MAP.md) | Из каких наблюдаемых чанков A–K состоит ремонт, порядок, параллелизм | зоны записи — файлы с номерами строк; статусы A–D |
| [../docs/RECOVERY-STATUS.md](../docs/RECOVERY-STATUS.md) | Что работает, что только в тесте, что не подключено, что требует рук | матрица «сценарий → статус → чем подтверждено» |
| [WHAT-WE-FOUND.md](WHAT-WE-FOUND.md) | Где процесс терял время — по замерам, без оценок личности | `metrics/wave-*.json`, `metrics/events.jsonl` |
| [../docs/PIPELINE-COMPARISON.md](../docs/PIPELINE-COMPARISON.md) | Почему выходило «сделано, но не работает» | те же машинные записи |
| [../docs/PIPELINE-TELEMETRY.md](../docs/PIPELINE-TELEMETRY.md) | Сколько заняли волны и чем это записано | 9 волн, ручных значений нет |
| [../docs/observability/ACCEPTANCE.md](../docs/observability/ACCEPTANCE.md) | Протокол приёмки любого ремонта и семь типов ложного зелёного | применялся к B, C и D |
| [../README.md](../README.md) | Статус в шесть строк и навигация по всем документам | `metrics/` |
| [../openspec/README.md](../openspec/README.md) | Как применяется OpenSpec: спека — принятое поведение, не план | архив change: 11 задач закрыты, 3 нет |
| [../metrics/r1-runtime.json](../metrics/r1-runtime.json) | Числа базового прогона R1 и список `not_verified` | сам файл: команды, секунды, SHA-256, окружение |
| [../metrics/chunk-b.json](../metrics/chunk-b.json), [chunk-c](../metrics/chunk-c.json), [chunk-d](../metrics/chunk-d.json) | RED-доказательство и приёмка каждого ремонта | логи прогонов вне репозитория, имена в файлах |
| [../provenance/source-manifest.json](../provenance/source-manifest.json) | 56 импортированных файлов и их SHA-256 | байты исходников не менялись |

## 2. Проверить за 15 минут

Из корня репозитория, Git Bash. Числа R1 сняты на `20eeb6d8`; числа ремонтов — на `93073fa`.

**Шаг 1 (2 мин) — ревизия и toolchain**

```bash
git rev-parse --abbrev-ref HEAD     # intake/verified-export-0.1.0
git log --oneline -1                # 93073fa
git diff --numstat 20eeb6d8..HEAD -- src   # две строки: 203/0 и 93/2
java -version                       # нужен 25.x (в прогонах: Temurin 25.0.5+8)
```

Две строки в `git diff` — это вся правка продукта. Любой третий файл или другие числа = код
на HEAD не тот, что в наших отчётах.

**Шаг 2 (3 мин, первая сборка дольше) — сборка и серверные тесты**

```bash
export GRADLE_USER_HOME="$PWD/.gradle-user-home"   # кэш держите вне checkout
./gradlew --no-daemon clean build
```

Ожидается `BUILD SUCCESSFUL` и `build/libs/minemod-0.1.0.jar`. На тёплом кэше у нас 52–57 с,
на первом прогоне 231 с: Loom скачивает зависимости. `clean build` сам выполняет `:runGameTest`.
JDK не скачивается сам (`org.gradle.java.installations.auto-download=false`): при JDK ниже 25
сборка упадёт — это ожидаемо.

**Шаг 3 (2 мин) — состав тестов считается из XML, а не с консоли**

```bash
XML=build/test-results/gameTest/game-test.xml
grep -o '<testcase' $XML | wc -l                 # 29
grep -o 'name="minemod-test:' $XML | wc -l       # 28
grep -o '<failure' $XML | wc -l                  # 0
grep -ro '@GameTest' src/gametest/java | wc -l   # 28
grep -o 'name="minemod-test:[^"]*"' $XML | sort
```

`grep -o … | wc -l`, а не `grep -c`: XML приходит одной строкой, и `grep -c` вернёт «1» при любом
числе кейсов — на этом легко принять пустой прогон за полный.

29 = 28 модовых + ванильный `minecraft:always_pass`; консольная сводка эти два вида не различает.
Среди имён должны быть `creative_player_is_targeted`, `retaliation_after_deflect`,
`creeper_front_damages` — это и есть три поведенческих ремонта.

**Шаг 4 (5 мин) — клиентский прогон, отдельный от серверного**

```bash
./gradlew --no-daemon runClientGameTest
ls src/main/resources/data/minemod   # loot_table, recipe — каталога advancement нет
```

Нужен дисплей и GL. Ожидается `BUILD SUCCESSFUL` (у нас 53–65 с на тёплом кэше, 250 с на первом)
и строка `Summoned new Shellback`: клиентский source set загружается, entrypoint не падает, мир
поднимается, команда принята. Вид моба, иконок и щита этим **не** проверяется; без дисплея
результат — «не проверено», а не PASS.

**Шаг 5 (3 мин) — проверить сами ремонты**

```bash
cat metrics/chunk-b.json metrics/chunk-c.json metrics/chunk-d.json
```

В каждом файле: что было сломано, каким тестом воспроизведено (RED), что изменено, чем принято и
что осталось непроверенным. Формулировка «не воспроизводится» тоже допустимый исход — по чанку D
первая гипотеза координатора оказалась неверной, и это записано честно.

Ручные шаги в игре (не обязательны для приёмки, но проверяют ощущение):

```text
/summon minemod:shellback ~ ~ ~2      → /gamemode creative → моб подходит и машет без урона
ударить в панцирь спереди, шаг назад  → моб идёт в ответную атаку
крипер строго перед мобом             → HP моба падает
```

## 3. Утверждение → доказательство → где лежит

| Утверждение | Чем подтверждено | Где лежит |
|---|---|---|
| Правка продукта — два файла, +296/−2 | `git diff --numstat 20eeb6d8..HEAD -- src` | этот файл, шапка |
| Сборка успешна, 29 кейсов, 0 падений | три чистые сборки подряд, XML `game-test.xml` | `metrics/chunk-d.json` |
| Креатив берётся в цель, урон не проходит | RED `target=null` → GREEN 27/27; тест `creative_player_is_targeted` | `metrics/chunk-b.json` |
| После отражения моб отвечает | RED `lastHurtByMob=null on tick 2` → GREEN 28/28 | `metrics/chunk-c.json` |
| Взрыв крипера снимает HP | RED `24.0 → 24.0` → GREEN `24.0 → 0.0`, тест `creeper_front_damages` | `metrics/chunk-d.json` |
| База не переписана | 56 файлов байт-в-байт, SHA-256 | `provenance/source-manifest.json` |
| «26/26» базовой ревизии воспроизведено нами | прогон R1 на `20eeb6d8`: 25 модовых + заглушка | `metrics/r1-runtime.json` |
| Визуал моба не подключён | `ShellbackRenderer` на ванильной модели, своя геометрия не запекается | `docs/RECOVERY-STATUS.md`, чанк G |
| Иконки пластины и яйца ванильные | `models/item/*.json` → `minecraft:item/armadillo_scute`, `…/silverfish_spawn_egg` | `docs/REPAIR-MAP.md`, чанк H |
| Окно парирования короче замаха | окно `[5,11]` при замахе 16 тиков, без нижней границы | `docs/REPAIR-MAP.md`, чанк E |
| Advancement отсутствует | в `data/minemod/` только `recipe` и `loot_table` | `docs/REPAIR-MAP.md`, чанк J |
| Спавн узкий, охват биомов не доказан | гейт ниже 50 блоков без неба и при свете; изменён 1 биом из 67 | `docs/REPAIR-MAP.md`, чанк K |

Визуал моба и иконки — «не подключён» / «чужие текстуры», спавн и ощущение боя — «требует
проверки руками»: это чтение кода и тики стенда, не игра живым игроком.

## 4. Чего здесь нет

- **Ремонтов E–K.** Остановились на D по решению владельца. E заблокирован развилкой F
  (чем игрок закрывается до апгрейда щита) — это решение владельца, не лаборатории.
- **Готового релиза.** Ни тега, ни опубликованного JAR; `origin/main` на `a863cb4`.
- **CI.** Любое «зелёно» здесь — локальный прогон с указанной ревизией, JDK и Gradle.
- **Гарантий безопасности зависимостей.** `gradle/verification-metadata.xml` отсутствует,
  транзитивные артефакты не проверялись.
- **Проверки ощущений.** Цикл «моб → бой → пластина → крафт → щит» живым игроком не проходил
  никто; пиксельный вид не проверен. Это записано как «не проверено», а не замазано.
