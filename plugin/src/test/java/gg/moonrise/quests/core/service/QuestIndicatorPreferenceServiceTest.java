package gg.moonrise.quests.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestIndicatorPreferenceServiceTest {

    private SqlProvider sqlProvider;
    private QuestIndicatorPreferenceService service;

    @BeforeEach
    public void setUp() {
        sqlProvider = mock(SqlProvider.class);
        when(sqlProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        service = new QuestIndicatorPreferenceService(sqlProvider);
    }

    @Test
    public void evictDropsCachedPreference() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, QuestIndicatorPreferenceService.Scope.PERSONAL, "chat");
        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, QuestIndicatorPreferenceService.Scope.PERSONAL));

        service.evict(playerId);

        assertEquals(Optional.empty(), service.cachedPreference(playerId, QuestIndicatorPreferenceService.Scope.PERSONAL));
    }
}
