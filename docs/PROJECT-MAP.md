# Карта проекта

Одна страница навигации: какой документ что решает и где в дереве живёт код.
Утверждения о поведении мода здесь не дублируются — они в
[RECOVERY-STATUS](RECOVERY-STATUS.md).

Стартовая реализация импортирована из upstream `f69785bbe15088b0d015c71d8cf187aa365c5079`.
Это уже существующий мод, а не результат разработки этой лаборатории
([BASELINE](BASELINE.md), [provenance](../provenance/README.md)).

## Документы

| Документ | Его единственная задача | Кому |
|---|---|---|
| [../README.md](../README.md) | Точка входа: статус в шесть строк и навигация | Всем, первым |
| `docs/PROJECT-MAP.md` (этот файл) | Где документ и где код | Тем, кто ищет файл |
| [RECOVERY-STATUS.md](RECOVERY-STATUS.md) | Что проверено, что не подключено, что не доказано | Владельцу, исполнителю, проверяющему |
| [REPAIR-MAP.md](REPAIR-MAP.md) | Из каких наблюдаемых чанков состоит ремонт, в каком порядке и что можно вести параллельно | Владельцу и координатору до постановки задач |
| [HANDOVER.md](HANDOVER.md) | Передача: как запускать, что доказано, чего нет | Первому автору и новому исполнителю |
| [../handover/README.md](../handover/README.md) | Индекс пакета передачи и проверка за 15 минут | Первому автору в первую очередь |
| [../handover/WHAT-WE-CHANGED.md](../handover/WHAT-WE-CHANGED.md) | Что добавлено и изменено по git, что не тронуто | При сверке «наше / ваше» |
| [../handover/WHAT-WE-FOUND.md](../handover/WHAT-WE-FOUND.md) | Где процесс терял время и чем это измерено | При разборе причин, без оценок личности |
| [../handover/WHAT-WE-REPAIRED.md](../handover/WHAT-WE-REPAIRED.md) | Что уже отремонтировано, чем доказано, сколько заняло | При приёмке первого среза |
| [BASELINE.md](BASELINE.md) | Что существовало до лаборатории и как точен импорт | Координатору при границе «наше / чужое» |
| [SECURITY.md](SECURITY.md) | Границы статического аудита и его негарантии | Координатору перед исполнением кода |
| [DECISIONS.md](DECISIONS.md) | Решения владельца и диспозиции находок | Координатору при спорной трактовке |
| [DELIVERY.md](DELIVERY.md) | Единица работы, параллелизм, приёмка, повтор | Координатору и исполнителю до старта |
| [PIPELINE-TELEMETRY.md](PIPELINE-TELEMETRY.md) | Замеры волн: время, исполнители, вызовы | Тем, кто считает стоимость пути |
| [PIPELINE-COMPARISON.md](PIPELINE-COMPARISON.md) | Разница с авторским процессом по измерениям | Для разбора «сделано, но не работает» |
| [E0-SCORECARD.md](E0-SCORECARD.md) | Числа статической волны E0 по агентам | Для истории и сравнения волн |
| [observability/README.md](observability/README.md) | Какая величина что означает, чем подтверждена | До чтения любого scorecard'а |
| [observability/R1-SCORECARD.md](observability/R1-SCORECARD.md) | Черновой срез R1 (нижние оценки, не приёмка) | Для сверки с `metrics/r1-*` |
| [observability/ACCEPTANCE.md](observability/ACCEPTANCE.md) | Протокол приёмки любого чанка: команды, успех, ложные зелёные | Перед каждым ремонтом и после него |
| [../openspec/README.md](../openspec/README.md) | Как применяется OpenSpec и чего он не делает | До оформления change |
| [../openspec/changes/archive/2026-11-19-verify-imported-baseline/proposal.md](../openspec/changes/archive/2026-11-19-verify-imported-baseline/proposal.md) | Предложение проверки baseline (заархивировано) | Координатору при планировании |
| [../provenance/README.md](../provenance/README.md) | Правила intake, атрибуция, лицензия | При сверке происхождения |
| [../provenance/source-manifest.json](../provenance/source-manifest.json) | 56 импортированных файлов и их SHA-256 | При проверке неизменности байтов |
| [guide-bounded-reviews.md](guide-bounded-reviews.md) | Как выйти из бесконечного ревью | Владельцу и координатору |
| [checklist-next-cycle.md](checklist-next-cycle.md) | Памятка на один рабочий цикл | Исполнителю перед стартом |

## Код

| Область кода | Где | Что делает |
|---|---|---|
| Инициализация мода | `src/main/java/dev/minemod/MinemodMod.java`; `src/main/resources/fabric.mod.json` | Регистрирует entrypoints, компоненты, предметы, сущности; `license: All-Rights-Reserved` |
| Панцирник: фазы, рывок, отражение | `src/main/java/dev/minemod/entity/` | Поведение моба и переходы состояний |
| Контракт реакции на удар | `src/main/java/dev/minemod/api/Staggerable.java` | Что значит «оглушён» для атакующего |
| Окно парирования | `src/main/java/dev/minemod/parry/ParryRules.java`, `ParryHandler.java` | Отсчёт окна, slowness/weakness, отбрасывание, ответ EXPOSED |
| Пластина и улучшение щита | `item/ModItems.java`, `component/ModComponents.java` | Предмет, компонент `minemod:plated`, сохранение компонентов входа |
| Рецепт | `src/main/resources/data/minemod/recipe/plated_shield.json` | Крафт «щит + пластина» (подсказки в игре нет — см. RECOVERY-STATUS) |
| Спавн, яйцо, добыча | `src/main/java/dev/minemod/spawn/`; `data/minemod/loot_table/entities/shellback.json` | Условия появления, спавн-яйцо, выпадение пластины |
| Активный клиент | `src/client/java/dev/minemod/client/MinemodClient.java`, `ShellbackClient.java`, `ShellbackRenderer.java`, `ItemTooltips.java` | Тултип и рендер на ванильной модели |
| Спящий арт | `client/ShellbackGeometry.java`, `client/shield/PlatedShieldClient.java`, `PlatedShieldSpecialRenderer.java` | Своя геометрия и рендерер щита: в дереве есть, не активированы |
| Тестовый стенд | `src/gametest/java/dev/minemod/test/`; `src/gametest/resources/fabric.mod.json` | 25 модовых сценариев плюс ванильная заглушка |
| Ресурсы | `src/main/resources/assets/minemod/` | Модели предметов, переводы en/ru, PNG |
| Генераторы и чекер арта | `tools/gen_textures.py`; `tools/art/plated_shield/` | Генерация PNG и проверка арта; в прогонах R1 не участвуют |
| Наблюдаемость | `tools/observability/collect_run.py` (+ self-test); `metrics/` | Сбор волны из машинных записей |
| Сборка | `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/` | Toolchain, задачи сборки и GameTest |

## Подключённое и спящее

Различие важнее наличия файлов: файл в дереве не означает доставленную фичу.
Клиентский entrypoint вызывает `ShellbackClient.init()` и `ItemTooltips.init()`; собственная
геометрия и special-renderer щита **не активированы**, иконки предметов ссылаются на ванильные
текстуры. Полный список статусов — в [RECOVERY-STATUS](RECOVERY-STATUS.md), здесь только
указатель, где лежит код.

## Правило ведения документов

- **Один документ — одна задача.** Если утверждение нужно второму документу, ставится ссылка,
  а не копия текста. Расхождение чинится в месте-источнике.
- **Новый документ добавляется в таблицу выше в том же коммите**, что и файл. Документа без
  строки в этой карте не существует.
- **Карта не пересказывает содержание.** Здесь задача и адресат, а не статусы, числа и правила.
- **Устаревшее удаляется, а не зачёркивается.** Заменённый документ либо удаляется, либо явно
  помечается как исторический с указателем на актуальный.

Исторические материалы (полный экспорт, старые планы, worktree, переписка, логи, Git bundle)
хранятся вне публичного дерева; правила — в [provenance](../provenance/README.md).
