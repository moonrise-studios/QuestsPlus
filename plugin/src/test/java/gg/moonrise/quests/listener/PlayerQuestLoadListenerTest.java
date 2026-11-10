package gg.moonrise.quests.listener;

import gg.moonrise.quests.application.indicator.QuestIndicatorPreferenceService;
import gg.moonrise.quests.application.indicator.QuestProgressIndicatorService;
import gg.moonrise.quests.application.reset.QuestResetService;
import gg.moonrise.quests.application.quest.QuestService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerQuestLoadListenerTest {

    private ServerMock server;
    private QuestService questService;
    private QuestResetService resetService;
    private QuestIndicatorPreferenceService preferenceService;
    private QuestProgressIndicatorService progressIndicatorService;
    private PlayerQuestLoadListener listener;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        questService = mock(QuestService.class);
        resetService = mock(QuestResetService.class);
        preferenceService = mock(QuestIndicatorPreferenceService.class);
        progressIndicatorService = mock(QuestProgressIndicatorService.class);
        listener = new PlayerQuestLoadListener(questService, resetService, preferenceService, progressIndicatorService);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void quitEvictsPerPlayerIndicatorState() {
        PlayerMock player = server.addPlayer("Leaver");

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        verify(progressIndicatorService).clearPlayer(player.getUniqueId());
        verify(preferenceService).evict(player.getUniqueId());
    }

    @Test
    public void failedJoinLoadsAreHandled() {
        PlayerMock player = server.addPlayer("Joiner");
        when(resetService.currentResetKey()).thenReturn("reset");
        when(questService.ensurePlayerStateAsync(player, "reset"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database offline")));
        when(preferenceService.loadPreference(player.getUniqueId()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database offline")));

        assertDoesNotThrow(() -> listener.onJoin(new PlayerJoinEvent(player, Component.empty())));

        verify(questService).ensurePlayerStateAsync(player, "reset");
        verify(preferenceService).loadPreference(player.getUniqueId());
    }
}
