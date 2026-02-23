# Деплой на Railway

## Переменные окружения (Variables)

В проекте Railway откройте ваш сервис → **Variables** и добавьте:

| Переменная | Обязательно | Пример | Описание |
|------------|-------------|--------|----------|
| `TELEGRAM_BOT_TOKEN` | Да | `8427746512:AAH5...` | Токен бота от @BotFather |
| `TELEGRAM_BOT_USERNAME` | Да | `evgegegegenia_bot` | Username бота (без @) |
| `PORT` | Нет | — | Railway подставляет сам, не трогать |
| `TELEGRAM_PRELOAD_CHAT_ID` | Нет | `745937957` | Ваш chatId для предзагрузки видео |
| `SPRING_PROFILES_ACTIVE` | Нет | `production` | Ставьте `production`, если используете переменные выше |

**Важно:** без `TELEGRAM_BOT_TOKEN` и `TELEGRAM_BOT_USERNAME` бот не заработает, но приложение всё равно запустится и healthcheck пройдёт (токен можно добавить позже и сделать Redeploy).

## Healthcheck

- Путь проверки: **`/health`** (настроено в `railway.toml`).
- Сервер слушает `0.0.0.0` и порт из `PORT`.
- Если проверка жизни всё равно не проходит — смотрите логи (View Logs): возможно, приложение падает до старта веб-сервера.

## После деплоя

1. Убедитесь, что в логах есть строка `Started ReHbApplication`.
2. Если есть `Failed to register Telegram bot` — проверьте переменные `TELEGRAM_BOT_TOKEN` и `TELEGRAM_BOT_USERNAME`, затем нажмите **Redeploy**.
