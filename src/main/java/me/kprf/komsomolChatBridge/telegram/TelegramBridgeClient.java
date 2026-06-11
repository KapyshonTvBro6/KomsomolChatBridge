package me.kprf.komsomolChatBridge.telegram;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.ChatBridgeService;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TelegramBridgeClient {
    private final Supplier<BridgeConfig> configSupplier;
    private final Executor executor;
    private final TelegramUpdateListener updateListener;
    private final HttpClient httpClient;
    private final Consumer<String> infoLogger;
    private final BiConsumer<String, Throwable> errorLogger;
    private final AtomicLong offset = new AtomicLong(0L);
    private volatile boolean running;
    private volatile boolean connected;
    private volatile HttpServer webhookServer;

    public TelegramBridgeClient(
            Supplier<BridgeConfig> configSupplier,
            ChatBridgeService chatBridgeService,
            Executor executor,
            Consumer<String> infoLogger,
            BiConsumer<String, Throwable> errorLogger
    ) {
        this.configSupplier = configSupplier;
        this.executor = executor;
        this.updateListener = new TelegramUpdateListener(configSupplier, chatBridgeService);
        this.infoLogger = infoLogger == null ? ignored -> { } : infoLogger;
        this.errorLogger = errorLogger == null ? (message, throwable) -> { } : errorLogger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
    }

    public synchronized void start() {
        BridgeConfig.TelegramSettings settings = configSupplier.get().telegram();
        if (!settings.enabled()) {
            infoLogger.accept("Telegram отключён в config.yml.");
            return;
        }
        if (settings.botToken() == null || settings.botToken().isBlank()) {
            infoLogger.accept("Telegram bot_token пустой, Telegram-клиент не запускается.");
            return;
        }
        if (settings.chatId() == null || settings.chatId().isBlank()) {
            infoLogger.accept("Telegram chat_id пустой, Telegram-клиент не запускается.");
            return;
        }

        shutdown();
        running = true;
        if (settings.useWebhook()) {
            startWebhook(settings);
        } else {
            startLongPolling();
        }
    }

    public synchronized void shutdown() {
        running = false;
        connected = false;
        HttpServer currentServer = webhookServer;
        webhookServer = null;
        if (currentServer != null) {
            currentServer.stop(0);
            deleteWebhook();
        }
    }

    public boolean isConfigured() {
        BridgeConfig.TelegramSettings settings = configSupplier.get().telegram();
        return settings.enabled()
                && settings.botToken() != null
                && !settings.botToken().isBlank()
                && settings.chatId() != null
                && !settings.chatId().isBlank();
    }

    public boolean isConnected() {
        return connected;
    }

    public CompletableFuture<String> sendMessage(String text, String replyToMessageId) {
        BridgeConfig.TelegramSettings settings = configSupplier.get().telegram();
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", settings.chatId());
        payload.addProperty("text", text);
        payload.addProperty("disable_web_page_preview", false);
        if (settings.parseMode() != null && !settings.parseMode().isBlank()) {
            payload.addProperty("parse_mode", settings.parseMode());
        }

        Integer replyMessageId = parseInteger(replyToMessageId);
        if (replyMessageId != null) {
            JsonObject replyParameters = new JsonObject();
            replyParameters.addProperty("message_id", replyMessageId);
            payload.add("reply_parameters", replyParameters);
        }

        return postTelegram("sendMessage", payload)
                .thenApply(response -> {
                    JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                    if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                        throw new IllegalStateException("Telegram sendMessage вернул ошибку: " + response);
                    }
                    JsonObject result = json.getAsJsonObject("result");
                    return result == null || !result.has("message_id") ? "" : result.get("message_id").getAsString();
                });
    }

    private void startLongPolling() {
        infoLogger.accept("Telegram long polling запускается.");
        CompletableFuture.runAsync(() -> {
            while (running) {
                try {
                    JsonObject payload = new JsonObject();
                    long currentOffset = offset.get();
                    if (currentOffset > 0) {
                        payload.addProperty("offset", currentOffset);
                    }
                    payload.addProperty("timeout", 25);
                    JsonArray allowedUpdates = new JsonArray();
                    allowedUpdates.add("message");
                    payload.add("allowed_updates", allowedUpdates);

                    HttpRequest request = telegramRequest("getUpdates", payload, Duration.ofSeconds(35));
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        connected = false;
                        infoLogger.accept("Telegram getUpdates вернул HTTP " + response.statusCode());
                        sleepQuietly(3);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                        connected = false;
                        infoLogger.accept("Telegram getUpdates вернул ok=false: " + response.body());
                        sleepQuietly(3);
                        continue;
                    }

                    connected = true;
                    for (var element : json.getAsJsonArray("result")) {
                        JsonObject update = element.getAsJsonObject();
                        if (update.has("update_id")) {
                            offset.updateAndGet(previous -> Math.max(previous, update.get("update_id").getAsLong() + 1));
                        }
                        updateListener.handleUpdate(update);
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception exception) {
                    connected = false;
                    errorLogger.accept("Ошибка Telegram long polling.", exception);
                    sleepQuietly(3);
                }
            }
        }, executor);
    }

    private void startWebhook(BridgeConfig.TelegramSettings settings) {
        try {
            BridgeConfig.TelegramWebhookSettings webhook = settings.webhook();
            HttpServer server = HttpServer.create(new InetSocketAddress(webhook.listenHost(), webhook.listenPort()), 0);
            server.createContext("/", this::handleWebhook);
            server.setExecutor(executor::execute);
            server.start();
            webhookServer = server;
            connected = true;
            setWebhook(webhook.publicUrl());
            infoLogger.accept("Telegram webhook слушает " + webhook.listenHost() + ':' + webhook.listenPort());
        } catch (IOException exception) {
            connected = false;
            errorLogger.accept("Не удалось запустить Telegram webhook-сервер.", exception);
        }
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Method Not Allowed");
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject update = JsonParser.parseString(body).getAsJsonObject();
            updateListener.handleUpdate(update);
            respond(exchange, 200, "OK");
        } catch (RuntimeException exception) {
            errorLogger.accept("Ошибка обработки Telegram webhook update.", exception);
            respond(exchange, 200, "OK");
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void setWebhook(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("url", publicUrl);
        postTelegram("setWebhook", payload)
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    errorLogger.accept("Не удалось установить Telegram webhook.", throwable);
                    return null;
                });
    }

    private void deleteWebhook() {
        JsonObject payload = new JsonObject();
        payload.addProperty("drop_pending_updates", false);
        postTelegram("deleteWebhook", payload)
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(throwable -> null);
    }

    private CompletableFuture<String> postTelegram(String method, JsonObject payload) {
        return httpClient.sendAsync(telegramRequest(method, payload, Duration.ofSeconds(20)), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Telegram " + method + " вернул HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return response.body();
                });
    }

    private HttpRequest telegramRequest(String method, JsonObject payload, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(apiUrl(method)))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
    }

    private String apiUrl(String method) {
        return "https://api.telegram.org/bot" + configSupplier.get().telegram().botToken() + '/' + method;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sleepQuietly(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
