package gg.moonrise.quests.core.service;

import gg.moonrise.quests.core.sql.SQLDatabase;
import gg.moonrise.quests.core.sql.SQLiteDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestIndicatorPreferenceServiceTest {

    private static final QuestIndicatorPreferenceService.Scope PERSONAL = QuestIndicatorPreferenceService.Scope.PERSONAL;
    private static final QuestIndicatorPreferenceService.Scope GLOBAL = QuestIndicatorPreferenceService.Scope.GLOBAL;

    private SqlProvider sqlProvider;
    private ResultSet resultSet;
    private QuestIndicatorPreferenceService service;
    private PendingLoad pendingLoad;

    @BeforeEach
    public void setUp() throws SQLException {
        sqlProvider = mock(SqlProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        when(sqlProvider.runAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(sqlProvider.getConnection()).thenReturn(connection);
        when(sqlProvider.currentDatabase()).thenReturn(SQLDatabase.of(new SQLiteDialect()));
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(sqlProvider.supplyAsync(any())).thenAnswer(invocation -> {
            pendingLoad = new PendingLoad(invocation.getArgument(0), new CompletableFuture<>());
            return pendingLoad.future();
        });
        service = new QuestIndicatorPreferenceService(sqlProvider);
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
    public void loadPopulatesCache() throws SQLException {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", null, "ACTION_BAR");

        CompletableFuture<QuestIndicatorPreferenceService.Preferences> load = service.loadPreference(playerId);
        pendingLoad.complete();

        assertEquals(new QuestIndicatorPreferenceService.Preferences("CHAT", "ACTION_BAR"), load.join());
        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, PERSONAL));
        assertEquals(Optional.of("ACTION_BAR"), service.cachedPreference(playerId, GLOBAL));
    }

    @Test
    public void loadDoesNotOverwriteSetPreferenceMadeWhileLoading() throws SQLException {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", "CHAT", "CHAT");

        CompletableFuture<QuestIndicatorPreferenceService.Preferences> load = service.loadPreference(playerId);
        service.setPreference(playerId, PERSONAL, "action_bar");
        pendingLoad.complete();

        assertEquals(Optional.of("ACTION_BAR"), service.cachedPreference(playerId, PERSONAL));
        assertEquals(Optional.empty(), service.cachedPreference(playerId, GLOBAL));
        assertEquals("ACTION_BAR", load.join().personal());
    }

    @Test
    public void loadDoesNotRestoreClearedPreference() throws SQLException {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "chat");
        storedRow("CHAT", "CHAT", null);

        service.loadPreference(playerId);
        service.clearPreference(playerId, PERSONAL);
        pendingLoad.complete();

        assertEquals(Optional.empty(), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void loadFinishingAfterQuitDoesNotRepopulateCache() throws SQLException {
        UUID playerId = UUID.randomUUID();
        storedRow("CHAT", "CHAT", "CHAT");

        service.loadPreference(playerId);
        service.evict(playerId);
        pendingLoad.complete();

        assertEquals(Optional.empty(), service.cachedPreference(playerId, PERSONAL));
    }

    @Test
    public void laterLoadResultIsAppliedAfterEarlierWrite() throws SQLException {
        UUID playerId = UUID.randomUUID();
        service.setPreference(playerId, PERSONAL, "action_bar");
        storedRow("CHAT", "CHAT", null);

        service.loadPreference(playerId);
        pendingLoad.complete();

        assertEquals(Optional.of("CHAT"), service.cachedPreference(playerId, PERSONAL));
    }

    private void storedRow(String legacy, String personal, String global) throws SQLException {
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("indicator_type")).thenReturn(legacy);
        when(resultSet.getString("personal_indicator_type")).thenReturn(personal);
        when(resultSet.getString("global_indicator_type")).thenReturn(global);
    }

    private record PendingLoad(SqlProvider.SqlSupplier<Object> supplier, CompletableFuture<Object> future) {

        private void complete() throws SQLException {
            future.complete(supplier.get());
        }
    }
}
