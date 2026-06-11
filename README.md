# KomsomolChatBridge

KomsomolChatBridge — Paper-плагин для единого моста сообщений между Minecraft, Discord и Telegram.

Маршрут всегда один: источник превращается в `BridgeMessage`, затем сообщение проходит через `ChatBridgeService`, `MessageRouter`, фильтр, антиспам и loop protection, а уже потом отправляется в нужные платформы.

## Возможности

- Minecraft → Discord и Telegram.
- Discord → Minecraft и Telegram.
- Telegram → Minecraft и Discord.
- Системные события: вход, выход, смерть, advancement, запуск и выключение сервера.
- Независимое включение/отключение Discord и Telegram.
- Discord webhook для красивых сообщений Minecraft от ника игрока.
- Telegram long polling по умолчанию и базовый webhook-режим.
- MiniMessage-форматы для Minecraft.
- SQLite-хранилище для привязок, message mapping и статистики.
- Антиспам, фильтр сообщений и защита от циклической пересылки.
- Публичный API для будущих плагинов КомсомолКрафта.

## Структура папок

```text
src/main/java/me/kprf/komsomolChatBridge
├── KomsomolChatBridge.java
├── KomsomolChatBridgePlugin.java
├── api
├── commands
├── config
├── core
├── discord
├── events
├── minecraft
├── storage
└── telegram

src/main/resources
├── config.yml
├── messages.yml
└── plugin.yml

src/test/java/me/kprf/komsomolChatBridge/core
├── LoopProtectionServiceTest.java
├── MessageFormatterTest.java
├── MessageRouterTest.java
└── RateLimitServiceTest.java
```

## Как собрать jar

Требования:

- Java 25.
- Gradle с поддержкой Java toolchains.

Команда сборки:

```bash
.\gradlew.bat clean jar
```

Готовый jar появится в:

```text
build/libs/KomsomolChatBridge-1.0-SNAPSHOT.jar
```

Зависимости JDA, Gson, SQLite JDBC и SLF4J упаковываются внутрь jar через Gradle `jar`.

## Как установить плагин

1. Остановите Paper-сервер.
2. Скопируйте `KomsomolChatBridge-1.0-SNAPSHOT.jar` в папку `plugins`.
3. Запустите сервер один раз, чтобы появились `config.yml` и `messages.yml`.
4. Остановите сервер.
5. Заполните `plugins/KomsomolChatBridge/config.yml`.
6. Запустите сервер снова.
7. Проверьте `/bridge status`.

Если `config.yml` или `messages.yml` уже были созданы старой версией без комментариев,
остановите сервер, переименуйте их в `config.old.yml` и `messages.old.yml`, затем
запустите сервер снова. Плагин создаст новые файлы с комментариями и примерами.

## Discord bot

1. Откройте [Discord Developer Portal](https://discord.com/developers/applications).
2. Создайте приложение.
3. В разделе `Bot` создайте бота.
4. Скопируйте token и вставьте его в `discord.bot_token`.
5. Включите `Message Content Intent`, если бот должен читать обычный текст сообщений.
6. В `OAuth2 → URL Generator` выберите scope `bot`.
7. Выдайте боту права читать и отправлять сообщения в нужном канале.
8. Пригласите бота на сервер.

### Как получить Discord channel_id

1. В Discord включите `Developer Mode`: `User Settings → Advanced → Developer Mode`.
2. Правой кнопкой нажмите на нужный канал.
3. Выберите `Copy Channel ID`.
4. Вставьте значение в `discord.channel_id`.
5. Для консольного канала вставьте отдельный ID в `discord.console_channel_id`.
6. Для команды `/bridge discord` укажите ссылку в `discord.discord_invite_link`.

Если используете webhook для Minecraft-сообщений:

1. В настройках канала откройте `Integrations → Webhooks`.
2. Создайте webhook.
3. Скопируйте webhook URL.
4. Вставьте его в `discord.webhook_url`.

## Telegram bot

1. Откройте Telegram и найдите `@BotFather`.
2. Выполните `/newbot`.
3. Задайте имя и username бота.
4. Скопируйте token и вставьте его в `telegram.bot_token`.
5. Добавьте бота в группу.
6. Напишите любое сообщение в группе.

### Как получить Telegram chat_id

Простой способ:

1. Вставьте token в URL:

```text
https://api.telegram.org/bot<TOKEN>/getUpdates
```

2. Откройте URL в браузере.
3. Найдите `chat.id`.
4. Для супергрупп ID обычно начинается с `-100`.
5. Вставьте значение в `telegram.chat_id`.

По умолчанию используется `LONG_POLLING`, это удобнее для локального Minecraft-сервера. Webhook-режим требует публичный HTTPS URL.

## Проверка

Minecraft → Discord:

1. Убедитесь, что `minecraft.relay_chat: true`.
2. Убедитесь, что `discord.enabled: true`.
3. Напишите сообщение в Minecraft.
4. Проверьте Discord-канал.

Minecraft → Telegram:

1. Убедитесь, что `telegram.enabled: true`.
2. Напишите сообщение в Minecraft.
3. Проверьте Telegram-группу.

Discord → Minecraft:

1. Убедитесь, что бот онлайн.
2. Напишите сообщение в указанном Discord-канале.
3. Сообщение должно появиться в Minecraft в формате `[DS] username: text`.

Telegram → Minecraft:

1. Убедитесь, что бот добавлен в нужную группу.
2. Напишите сообщение в Telegram.
3. Сообщение должно появиться в Minecraft в формате `[TG] username: text`.

Команды для проверки:

```text
/bridge status
/bridge discord
/bridge test discord
/bridge test telegram
```

## Диагностика ошибок

- Проверьте, что сервер запущен на Java 25.
- Проверьте `plugins/KomsomolChatBridge/config.yml`.
- Включите `general.debug: true`.
- Проверьте, что Discord bot token и Telegram bot token не пустые.
- Проверьте `discord.channel_id`, `discord.console_channel_id` и `telegram.chat_id`.
- Для Discord включите `Message Content Intent`.
- Убедитесь, что бот Discord имеет доступ к каналу.
- Убедитесь, что Telegram-бот находится в группе.
- Проверьте, что SQLite-файл доступен для записи.
- Посмотрите `/bridge status` и лог сервера.

## Публичный API

Будущий плагин может отправить системное сообщение так:

```java
KomsomolChatBridge.getApi().sendSystemMessage("[СИСТЕМА]: Обнаружен разлом на координатах X Z");
```

Доступные методы:

- `sendSystemMessage(String message)`
- `sendSystemMessage(Component component)`
- `sendToDiscord(String message)`
- `sendToTelegram(String message)`
- `broadcastBridgeMessage(BridgeMessage message)`

События:

- `BridgeMessageReceivedEvent`
- `BridgeMessageSentEvent`
- `BridgeMessageBlockedEvent`

## Что можно добавить позже

- Привязка Discord/Telegram аккаунтов к Minecraft.
- Роли Discord ↔ LuckPerms.
- Whitelist через Telegram/Discord.
- Отправка скриншотов и картинок.
- Web-панель.
- Интеграция с KomsomolAuth.
- Интеграция с лор-плагином КомсомолКрафта.
