package gg.moonrise.quests.application.indicator;

import gg.moonrise.quests.infrastructure.persistence.QuestIndicatorPreferenceRepository.StoredPreferences;
import gg.moonrise.quests.infrastructure.persistence.QuestIndicatorPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestIndicatorPreferenceServiceTest {

    private static final QuestIndicatorPreferenceService.Scope PERSONAL = QuestIndicatorPreferenceService.Scope.PERSONAL;
    private static final QuestIndicatorPreferenceService.Scope GLOBAL = QuestIndicatorPreferenceService.Scope.GLOBAL;

    private QuestIndicatorPreferenceRepository repository;
    private QuestIndicatorPreferenceService service;
    private CompletableFuture<Optional<StoredPreferences>> pendingLoad;
    private Optional<StoredPreferences> storedRow = Optional.empty();

    @BeforeEach
    public void setUp() {
        repository = mock(QuestIndicatorPreferenceRepository.class);
        when(repository.save(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.delete(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(repository.load(any())).thenAnswer(invocation -> {
            pendingLoad = new CompletableFuture<>();
            return pendingLoad;
        });
        service = new QuestIndicatorPreferenceService(repository);
    }

    @Test
    public void evictDropsCachedPreference() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "chat");
        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, PERSONAL));

        service.evict(playerId);

        assertEquals(Optional.empty(), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void loadPopulatesCache() {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", null, "ACTION_BAR");

        CompletableFuture<QuestIndicatorPreferenceService.Preferences> load = service.loadPreference(playerId);
        completePendingLoad();

        assertEquals(new QuestIndicatorPreferenceService.Preferences("CHAT", "ACTION_BAR"), load.join());
        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, PERSONAL));
        assertEquals(Optional.of("ACTION_BAR"), service.cachedPreference(playerId, GLOBAL));
    }

    @Test
    public void loadDoesNotOverwriteSetPreferenceMadeWhileLoading() {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", "CHAT", "CHAT");

        CompletableFuture<QuestIndicatorPreferenceService.Preferences> load = service.loadPreference(playerId);
        service.setPreference(playerId, PERSONAL, "action_bar");
        completePendingLoad();

        assertEquals(Optional.of("ACTION_BAR"), service.cachedPreference(playerId, PERSONAL));
        assertEquals(Optional.empty(), service.cachedPreference(playerId, GLOBAL));
        assertEquals("ACTION_BAR", load.join().personal());
    }

    @Test
    public void loadDoesNotRestoreClearedPreference() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "chat");
        storedRow("CHAT", "CHAT", null);

        service.loadPreference(playerId);
        service.clearPreference(playerId, PERSONAL);
        completePendingLoad();

        assertEquals(Optional.empty(), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void loadFinishingAfterQuitDoesNotRepopulateCache() {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", "CHAT", "CHAT");

        service.loadPreference(playerId);
        service.evict(playerId);
        completePendingLoad();

        assertEquals(Optional.empty(), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void laterLoadResultIsAppliedAfterEarlierWrite() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "action_bar");
        storedRow("CHAT", "CHAT", null);

        service.loadPreference(playerId);
        completePendingLoad();

        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void legacyColumnFillsScopesWithoutTheirOwnPreference() {
        UUID playerId = UUID.randomUUID();
        storedRow("chat", null, " ");

        CompletableFuture<QuestIndicatorPreferenceService.Preferences> load = service.loadPreference(playerId);
        completePendingLoad();

        assertEquals(new QuestIndicatorPreferenceService.Preferences("CHAT", "CHAT"), load.join());
    }

    @Test
    public void setPreferenceStoresScopedValueAndLegacyFallback() {
        UUID playerId = UUID.randomUUID();

        service.setPreference(playerId, GLOBAL, "chat");

        verify(repository).save(playerId, new StoredPreferences("CHAT", null, "CHAT"));
    }

    @Test
    public void clearingTheLastScopeDeletesTheRow() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "chat");

        service.clearPreference(playerId, PERSONAL);

        verify(repository).delete(playerId);
    }

    @Test
    public void clearingOneScopeKeepsTheOtherStored() {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "chat");
        service.setPreference(playerId, GLOBAL, "action_bar");

        service.clearPreference(playerId, PERSONAL);

        verify(repository).save(playerId, new StoredPreferences("ACTION_BAR", null, "ACTION_BAR"));
        verify(repository, never()).delete(any());
    }

    private void storedRow(String legacy, String personal, String global) {
        storedRow = Optional.of(new StoredPreferences(legacy, personal, global));
    }

    private void completePendingLoad() {
        pendingLoad.complete(storedRow);
    }
}
