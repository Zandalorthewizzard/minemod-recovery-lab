# Публичный слой наблюдаемости

Каталог отвечает на один вопрос: **что именно произошло в волне и чем это
подтверждено**, не превращая отчёт агента в факт. Здесь нет новых функций мода,
нового рантайма и новых продуктовых обещаний — только измеримый след запуска:
кто стартовал, когда, с какой моделью/провайдером, сколько вызовов сделал, какие
команды выполнил и чем они закончились.

- Текущий срез волны: [R1-SCORECARD.md](R1-SCORECARD.md) (черновой snapshot).
- Машинные файлы: [metrics/r1-20261119T212411Z](../../metrics/r1-20261119T212411Z/run.json).
- Инструмент: [tools/observability/collect_run.py](../../tools/observability/collect_run.py).
- Self-test: [tools/observability/test_collect_run.py](../../tools/observability/test_collect_run.py).

## Что считается разными величинами

| Величина | Как получается | Чем **не** является |
|---|---|---|
| Elapsed от GO | `as_of − authorization.timestamp` | не время работы агентов |
| Orchestration before dispatch | `dispatched_at − authorization.timestamp` | не «накладные расходы модели» |
| Wall стадии | `stage_end − dispatched_at` | не сумма работы агентов |
| Worker sum | простая сумма интервалов сессий | **не аддитивна** при параллельности |
| Worker union | объединение интервалов | не «ускорение» и не сэкономленное время |
| Peak / average active | sweep по start/end | не «производительность» |
| Command duration | `end_utc − start_utc` из ledger | не равно длительности агента |
| Tokens / cost | `null` без однозначной семантики | не 0, не «бесплатно» |

Сумма интервалов и union публикуются рядом с формулой и исходными метками
времени, чтобы читатель мог пересчитать, а не поверить.

## Инварианты слоя

1. **Однократность.** Один запуск = один snapshot. Нет polling, демона, sleep и
   ожидания завершения волны. Незавершённое остаётся `null`/`pending`/`open`.
2. **Read-only.** База открывается `mode=ro`; читается только whitelist
   метаданных (`async_delegations`, `sessions`). Промпты, цели, транскрипты,
   tool payloads и системные промпты не читаются и не пишутся.
3. **Отсутствие ≠ завершение.** Нет записи о делегировании — exit code 2 и
   ошибка, а не «волна прошла». Нет `exit_code` у команды — `incomplete`, а не
   успех. Нет `ended_at` — `open_at_snapshot`, а не длительность.
4. **Санитизация как преобразование, а не как обещание.** Локальные абсолютные
   пути заменяются семантическими именами, session id — детерминированными
   псевдонимами и односторонним `identity_sha256`; email, host-share, bearer,
   key-like и password-like строки редактируются. Исходные SHA-256 и список
   преобразований лежат в `provenance.json`.
5. **Детерминизм.** При одинаковом frozen input и фиксированном `--as-of`
   выходные файлы побайтово идентичны (проверяется тестом и файлом
   `determinism.txt`).
6. **Разделение «написано» и «принято».** Все публикации помечены
   `delivery_status: draft_snapshot`, `accepted: false`; принятие — вердикт
   координатора, а не поле коллектора.
7. **Никакой сети, никаких секретов, никакой конфигурации credential/host.**

## Запуск

```bash
python tools/observability/collect_run.py \
  --manifest <private-run-dir>/authorization.json \
  --delegation-id <actual Hermes delegation id> \
  --state-db <hermes-home>/state.db \
  --output metrics/<run-id> \
  --as-of 2026-11-19T21:35:39Z \
  [--roster <private labels.json>] \
  [--coordinator-events <private events.jsonl>] \
  [--commands <private aggregated ledger.jsonl>]
```

`--as-of` фиксирует момент снимка (нужен для воспроизводимости). `--commands`
принимает JSONL с полями `worker, phase, attempt, argv, start_utc, end_utc,
timeout_seconds, exit_code, log_file`; `argv` и `cwd` санитизируются.

Выход в `--output`: `run.json`, `workers.json`, `events.jsonl`, `commands.txt`,
`provenance.json`. Все значения времени — UTC ISO-8601; JSON пишется с
`sort_keys` и LF, чтобы хэши были стабильны.

## E0 в этом слое — статический intake

E0 был статическим аудитом и документацией: код не исполнялся, сборка,
GameTest и клиент не запускались, продукт не менялся
([E0-SCORECARD.md](../E0-SCORECARD.md), [e0-wave.json](../../metrics/e0-wave.json),
[e0-events.jsonl](../../metrics/e0-events.jsonl)). R1 добавляет runtime-проверки.
Сравнение двух волн помечено `comparison_unknown`: предмет, состав ролей и
условия различаются, сопоставимой контрольной базы автора нет. Из E0 повторно
использован только способ разбора фактических Hermes records, а не выводы о
скорости.

## Проверка

```bash
python tools/observability/test_collect_run.py -v
```

Тест строит синтетические фикстуры во временном каталоге (баннер
`SYNTHETIC-FIXTURE`, вне настоящих измерений) и проверяет: арифметику
перекрывающихся интервалов, незавершённого worker, отсутствующие колонки схемы,
санитизацию путей/секретоподобных строк, отказ при отсутствующем делегировании,
валидацию command ledger и побайтовый детерминизм. Отдельно проверяются ссылки
в этих документах и совпадение SHA-256 опубликованных файлов с `provenance.json`.

Результаты прогонов сохраняются рядом с данными:
[selftest-synthetic.txt](../../metrics/r1-20261119T212411Z/selftest-synthetic.txt),
[determinism.txt](../../metrics/r1-20261119T212411Z/determinism.txt).

## Границы

- Это **не** OS-sandbox: слой ничего не изолирует и не защищает хост.
- Метрики не доказывают качество кода, прохождение тестов или игровой сценарий;
  они фиксируют наблюдаемые интервалы, вызовы и exit-коды.
- Публичный слой не заменяет private receipts: он даёт координатору достаточно
  provenance для сверки, но не публикует сырые логи, `state.db`, промпты и пути.
- `.log` и `evidence/` в репозитории gitignored; публикуемые журналы пишутся
  как `.txt` внутри `metrics/<run-id>/`.
