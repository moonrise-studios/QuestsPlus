package gg.moonrise.quests.core.repository;

import gg.moonrise.quests.core.sql.SqliteTestHarness;
import gg.moonrise.quests.core.sql.SqlProvider;
import gg.moonrise.quests.core.repository.QuestIndicatorPreferenceRepository.StoredPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestIndicatorPreferenceRepositoryDatabaseTest {

    @TempDir
    private Path tempDir;

    private SqlProvider sqlProvider;
    private QuestIndicatorPreferenceRepository repository;
    private UUID playerId;

    @BeforeEach
    public void setUp() {
        sqlProvider = SqliteTestHarness.open(tempDir);
        repository = new QuestIndicatorPreferenceRepository(sqlProvider);
        playerId = UUID.randomUUID();
    }

    @AfterEach
    public void tearDown() {
        if (sqlProvider != null) {
            sqlProvider.onDisable();
        }
    }

    @Test
    public void loadIsEmptyWhenNoRowIsStored() {
        assertEquals(Optional.empty(), repository.load(playerId).join());
    }

    @Test
    public void savedPreferencesRoundTripWithNullScopes() {
        StoredPreferences stored = new StoredPreferences("CHAT", "CHAT", null);
        repository.save(playerId, stored).join();

        assertEquals(Optional.of(stored), repository.load(playerId).join());
    }

    @Test
    public void saveReplacesScopedColumnsAndKeepsTheLegacyColumn() {
        repository.save(playerId, new StoredPreferences("CHAT", "CHAT", null)).join();

        repository.save(playerId, new StoredPreferences("ACTION_BAR", null, "ACTION_BAR")).join();

        assertEquals(Optional.of(new StoredPreferences("CHAT", null, "ACTION_BAR")), repository.load(playerId).join());
    }

    @Test
    public void deleteRemovesOnlyThatPlayersRow() {
        UUID otherPlayer = UUID.randomUUID();
        StoredPreferences stored = new StoredPreferences("CHAT", "CHAT", null);
        repository.save(playerId, stored).join();
        repository.save(otherPlayer, stored).join();

        repository.delete(playerId).join();

        assertEquals(Optional.empty(), repository.load(playerId).join());
        assertEquals(Optional.of(stored), repository.load(otherPlayer).join());
    }
}
