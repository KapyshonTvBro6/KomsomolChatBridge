package me.kprf.komsomolChatBridge.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopProtectionServiceTest {
    @Test
    void duplicateInternalIdIsBlocked() {
        LoopProtectionService service = new LoopProtectionService(10, Duration.ofMinutes(5));
        UUID id = UUID.randomUUID();
        BridgeMessage message = BridgeMessage.builder(BridgePlatform.DISCORD)
                .internalId(id)
                .plainText("Привет")
                .build();

        assertTrue(service.markIfNew(message));
        assertFalse(service.markIfNew(message));
    }

    @Test
    void metadataBridgeIdIsAlsoBlocked() {
        LoopProtectionService service = new LoopProtectionService(10, Duration.ofMinutes(5));
        UUID bridgeId = UUID.randomUUID();
        BridgeMessage first = BridgeMessage.builder(BridgePlatform.MINECRAFT)
                .internalId(bridgeId)
                .plainText("Привет")
                .build();
        BridgeMessage echoed = BridgeMessage.builder(BridgePlatform.DISCORD)
                .plainText("Привет")
                .metadata("bridge_internal_id", bridgeId.toString())
                .build();

        assertTrue(service.markIfNew(first));
        assertFalse(service.markIfNew(echoed));
    }
}
