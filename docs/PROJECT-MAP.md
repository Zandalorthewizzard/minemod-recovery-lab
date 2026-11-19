# Карта проекта

Стартовая реализация импортирована из upstream-коммита `f69785bbe15088b0d015c71d8cf187aa365c5079`. Это уже существующий мод, а не результат разработки этой лаборатории.

## Порядок чтения

1. [Исходное состояние](BASELINE.md) — что уже есть и что ещё не проверено нами.
2. [Безопасность и границы проверки](SECURITY.md).
3. [Решения владельца и судьба замечаний](DECISIONS.md).
4. [Порядок поставки и измерений](DELIVERY.md).
5. [Фактический scorecard E0](E0-SCORECARD.md).
6. [Постепенный OpenSpec](../openspec/README.md).
7. [Гайд ограниченных ревью](guide-bounded-reviews.md) и [памятка цикла](checklist-next-cycle.md) — сохранены из исходного lab-репозитория.

## Куда смотреть в коде

| Область | Вход / основные файлы |
|---|---|
| Инициализация | `src/main/java/dev/minemod/MinemodMod.java`; `src/main/resources/fabric.mod.json` |
| Моб, фазы, рывок, отражение | `src/main/java/dev/minemod/entity/` |
| Контракт реакции на парирование | `src/main/java/dev/minemod/api/Staggerable.java` |
| Окно парирования и оглушение | `src/main/java/dev/minemod/parry/` |
| Компонент и пластина | `src/main/java/dev/minemod/component/`; `item/` |
| Рецепт | `src/main/resources/data/minemod/recipe/plated_shield.json` |
| Спавн, яйцо, добыча | `src/main/java/dev/minemod/spawn/`; `data/minemod/loot_table/entities/shellback.json` |
| Активный клиент | `src/client/java/dev/minemod/client/MinemodClient.java`, `ShellbackClient.java`, `ShellbackRenderer.java`, `ItemTooltips.java` |
| Серверные и клиентские проверки | `src/gametest/java/dev/minemod/test/`; `src/gametest/resources/fabric.mod.json` |
| Иконки, модели, переводы | `src/main/resources/assets/minemod/` |
| Генераторы ассетов | `tools/gen_textures.py`; `tools/art/plated_shield/` — в этом цикле не запускались |
| Сборка | `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/` |

## Подключённое и спящее — не одно и то же

Клиентский entrypoint вызывает `ShellbackClient.init()` и `ItemTooltips.init()`. Моб использует ванильную silverfish-модель; щит — ванильный облик с блеском и подсказкой.

`ShellbackGeometry`, `client/shield/PlatedShieldClient` и собственный рендерер щита находятся в дереве, но не активированы. Собственные PNG сохранены, однако текущие item-модели ссылаются на ванильные текстуры. Наличие файлов не считается доставленной визуальной фичей.

## Исторические материалы

Полный экспорт, старые планы, worktree, кандидаты, переписка, логи и Git bundle сохранены локально вне публичного репозитория. Здесь размещены только source-only baseline, обезличенные выводы и наши новые рабочие документы.

Не нужно восстанавливать всю историю перед каждой задачей. Для изменения достаточно затронутого кода, действующего контракта, относящихся к нему решений и свежих проверок.
