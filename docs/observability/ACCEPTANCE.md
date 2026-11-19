# Протокол приёмки чанка

Один порядок действий для любого чанка из [`REPAIR-MAP.md`](../REPAIR-MAP.md): команды, критерий успеха,
ложные зелёные. Вердикт — **принято**, **не принято** или **не проверено**, а не «зелёный тест».

## 1. Когда применяется

- **До чанка:** фиксируется база (§2, шаг 1) — иначе результат не к чему привязать.
- **После чанка:** протокол целиком, с клиентским прогоном, даже если правка серверная.
- **После правки принятого:** повтор целиком; старые числа к новой ревизии не относятся ([`DELIVERY.md`](../DELIVERY.md)); с чанка B — ещё отрицательная проверка и ручной шаг (§4).

## 2. Шаги

Git Bash из корня репозитория; `JAVA_HOME` — JDK 25, `GRADLE_USER_HOME` вне checkout ([`HANDOVER.md`](../HANDOVER.md) §4).

**Шаг 1 — ревизия и чистота дерева.**
```bash
git rev-parse --abbrev-ref HEAD     # intake/verified-export-0.1.0
git rev-parse HEAD                  # эта строка идёт в запись приёмки
git status --porcelain              # пусто = чисто
```

Непустой вывод — стоп: проверяется не то, что станет базой. Координатор коммитит или приёмка не начинается.

**Шаг 2 — `clean build` и exit code.**
```bash
time ./gradlew --no-daemon clean build; echo "exit=$?"
```

Считать `exit=` этого вызова (`0`), а не `BUILD SUCCESSFUL` из чужого лога; `clean` и `--no-daemon` — чтобы не подхватился кэш прошлой правки; выход `build/libs/minemod-0.1.0.jar`, время — из `time`, не из XML.

**Шаг 3 — серверные GameTests: состав из XML, не из консольной сводки.**
```bash
XML=build/test-results/gameTest/game-test.xml      # путь задан в build.gradle:33
grep -c '<testcase' $XML                            # всего кейсов
grep -c 'name="minemod-test:' $XML                  # модовые
grep -c 'name="minecraft:always_pass"' $XML         # ванильная заглушка
grep -c '<failure' $XML; grep -c '<error' $XML; grep -c '<skipped' $XML   # все три = 0
grep -rho '@GameTest' src/gametest/java | wc -l     # ожидаемое число модовых
```

Состав: `модовые == @GameTest в src/gametest/java`, `всего == модовые + 1`, три счётчика отказов по нулю; на базе `20eeb6d8` это 25 + `minecraft:always_pass` = 26 при 0 падений ([`metrics/r1-gametests.json`](../../metrics/r1-gametests.json)).
26 — не догма: кейс чанка поднимает обе стороны; `always_pass` — заглушка Fabric, покрытием не считается.

**Шаг 4 — клиентский прогон, отдельный артефакт.**
```bash
time ./gradlew --no-daemon runClientGameTest; echo "exit=$?"
find build/test-results -name '*.xml' | sort      # клиентский отчёт — отдельный файл
find . -name 's7-*.png' -path '*screenshots*'     # s7-shellback-smoke, s7-plated-shield-tooltip
```

| | Серверный `runGameTest` | Клиентский `runClientGameTest` |
|---|---|---|
| Что поднимается | выделенный сервер, headless | настоящий клиент, нужны дисплей и GL |
| Что исполняется | `@GameTest`-сценарии | `FabricClientGameTest` (`ClientSmokeTest.runTest`) |
| Отчёт | `build/test-results/gameTest/game-test.xml` | другой файл под `build/test-results`: `build.gradle:33` задаёт путь только серверному прогону |
| Что доказывает | сценарии внутри стенда | клиентский source set грузится, entrypoint не падает, мир и `/summon` работают |

Серверный XML **не** доказывает клиент: клиентских кейсов в нём нет. Без дисплея шаг 4 — «не проверено», а
не PASS; внешний вид не доказывает и клиентский прогон (`not_verified` в [`metrics/r1-runtime.json`](../../metrics/r1-runtime.json)).

**Шаг 5 — время и окружение.**
```bash
date -u +%FT%TZ; java -version
grep -E 'minecraft_version|loader_version|fabric_api_version|loom_version' gradle.properties
```

В запись: commit, wall сборки и клиента, JDK (в R1 — Temurin 25.0.5+8), Gradle 9.7.0, MC 26.3, Loader
0.20.1, Fabric API 0.165.2+26.3. Время из XML — длительность сьюта, не производительность.

## 3. Что считается успехом и что — ложным зелёным

Успех: шаги 1–5 на зафиксированной ревизии, состав XML совпал поимённо, есть клиентский артефакт, отрицательная проверка чанка и ручной шаг с записанным наблюдением.

| Ложный зелёный | Почему не успех |
|---|---|
| Зелёный без клиента | автор имел `24/24` при пустом `ShellbackClient.init` — клиентского слоя не было ([`RECOVERY-STATUS.md`](../RECOVERY-STATUS.md)) |
| Скриншоты без сравнения | `ClientSmokeTest.java:47,78` только снимает кадры; рендерер, рисующий пустоту, проходит |
| Assert по ключу вместо текста | тултип сверяется через `TranslatableContents#getKey`; удаление строк из `en_us.json`/`ru_ru.json` зелёное, игрок видит сырой ключ |
| Тест в тике 0 | `full_loop` пишет `completed at tick 0`: четыре вызова в одном тике, без времени, подбора, инвентаря и верстака |
| Шорткаты `kill()`, `setTarget`, `NoAI` | `kill()` обходит путь урона; `setTarget` подставляет цель в обход селектора — то, что чинит чанк B; `NoAI` замораживает фазы (на нём 4 из 6 боевых тестов) |
| Негативный тест при мёртвой фиче | с выключенным `ParryHandler.init()` зелёными остаются 21 из 26: `assertNotStaggered` верен и без парирования |
| Кейсы о ванили | три проверки свежесозданного `new ItemStack(Items.SHIELD)` в `ShieldUpgradeGameTest` не могут упасть из-за кода мода |

## 4. Минимальный набор для чанка B (цель в креативе)

| Что | Критерий | Как проверяется |
|---|---|---|
| Позитивный тест | в `GameType.CREATIVE` цель моба — игрок | `creative_player_is_targeted`: `getTarget() == player`, проверка после 20 тиков |
| Негативный тест | урон по креативу не проходит | удар в креативе → `getHealth()` не меняется (после контакта, а не только до); в выживании HP убывает |
| Ручной шаг | замах ≤1 с в креативе, удар в выживании | `/summon minemod:shellback ~ ~ ~5` → `/gamemode creative` → `/gamemode survival` |
| Что видно | разворот на игрока, `RAVAGER_ROAR` + `LARGE_SMOKE` (TELEGRAPH), затем рывок; в выживании удар, в креативе замах без потери HP | глазами в клиенте, 5 секунд на режим |

Ванильное правило «креатив не враг» остаётся: фиксируется, что урон креативу **не** наносится.

## 5. Куда пишется результат

- `metrics/<wave>-acceptance-<chunk>.json` по схеме [`r1-runtime.json`](../../metrics/r1-runtime.json): `commit`, `environment`, `build{command, exit, seconds}`, `server_gametests{file, tests, mod_tests, failed, error, skipped}`, `client_gametest{command, exit, seconds, report, screenshots}`, `negative_checks[]`, `manual_step{commands, observed}`, `verdict`, `not_verified[]`.
- Артефакты (`game-test.xml`, клиентский XML, скриншоты) остаются в `build/` и `run/` (gitignored); в записи — путь и число кейсов.
- Строка в [`RECOVERY-STATUS.md`](../RECOVERY-STATUS.md): сценарий → статус → чем подтверждено, статусы только из словаря файла.
- «Принято» без записи в `metrics/` не существует; запись без строки в матрице — тоже.

## 6. Ограничения

- Принудительных таймаутов нет, бюджеты процедурные; таймаут даёт «не проверено», а не PASS.
- CI нет: «зелёно» здесь — локальный прогон с указанной ревизией, JDK и Gradle, не проверка на чужой машине.
- Тяжёлый прогон (сборка, `runGameTest`, клиент) — один на волну; дубль не запускается, остальные ждут результата ([`REPAIR-MAP.md`](../REPAIR-MAP.md), «Линии параллелизма»).
- Протокол не вводит флагов сборки, пиксельных тестов, выделенного сервера и мультиплеера.
