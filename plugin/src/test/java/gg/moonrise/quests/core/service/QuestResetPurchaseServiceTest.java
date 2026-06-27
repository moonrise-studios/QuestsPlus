package gg.moonrise.quests.core.service;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestResetPurchaseServiceTest {

    @Test
    void beginBlocksDuplicateAttemptsUntilFinish() {
        QuestResetPurchaseService service = new QuestResetPurchaseService(mock(ConfigProvider.class));
        Player player = player(UUID.randomUUID());

        assertTrue(service.begin(player));
        assertFalse(service.begin(player));
        assertTrue(service.isProcessing(player));

        service.finish(player);

        assertFalse(service.isProcessing(player));
        assertTrue(service.begin(player));
    }

    @Test
    void finishIsIdempotentForRetiredAndCompletionCallbacks() {
        QuestResetPurchaseService service = new QuestResetPurchaseService(mock(ConfigProvider.class));
        Player player = player(UUID.randomUUID());

        assertTrue(service.begin(player));

        service.finish(player);
        service.finish(player);

        assertFalse(service.isProcessing(player));
        assertTrue(service.begin(player));
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }
}
