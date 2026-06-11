package me.kprf.komsomolChatBridge.discord;

import me.kprf.komsomolChatBridge.config.BridgeConfig;
import me.kprf.komsomolChatBridge.core.BridgeMessage;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class DiscordWebhookSender {
    private final Supplier<BridgeConfig> configSupplier;
    private final HttpClient httpClient;

    public DiscordWebhookSender(Supplier<BridgeConfig> configSupplier) {
        this.configSupplier = configSupplier;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        return settings.enabled()
                && settings.webhookUrl() != null
                && !settings.webhookUrl().isBlank();
    }

    public CompletableFuture<String> send(BridgeMessage message) {
        BridgeConfig.DiscordSettings settings = configSupplier.get().discord();
        JsonObject payload = new JsonObject();
        payload.addProperty("content", message.formattedText());
        payload.addProperty("username", message.displayName());
        String avatarUrl = settings.webhookAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            payload.addProperty("avatar_url", avatarUrl.replace("{player}", message.displayName()));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.webhookUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Discord webhook вернул HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return "";
                });
    }
}
