package gg.moonrise.quests.domain.model;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerQuestStateTest {

    private static final int GOAL = 1_000_000;

    @Test
    public void returnedCollectionsAreImmutableSnapshots() {
        GeneratedQuest quest = quest(0);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(quest), Map.of("easy", 1), Set.of("easy:1"));
        List<GeneratedQuest> before = state.quests();

        state.replaceQuest(quest.withProgress(5, false));
        state.setDifficultyCompletions("easy", 2);

        assertEquals(0, before.getFirst().progress());
        assertEquals(5, state.quests().getFirst().progress());
        assertEquals(2, state.difficultyCompletions("easy"));
        assertThrows(UnsupportedOperationException.class, () -> state.quests().add(quest(1)));
        assertThrows(UnsupportedOperationException.class, () -> state.difficultyCompletions().put("hard", 1));
        assertThrows(UnsupportedOperationException.class, () -> state.executedMilestones().add("hard:1"));
    }

    @Test
    public void putQuestIfSlotEmptyOnlyFillsEmptySlots() {
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(quest(1)));
        GeneratedQuest competing = quest(1);
        GeneratedQuest fresh = quest(0);

        assertFalse(state.putQuestIfSlotEmpty(competing));
        assertTrue(state.putQuestIfSlotEmpty(fresh));
        assertEquals(List.of(0, 1), state.quests().stream().map(GeneratedQuest::slotIndex).toList());
        assertEquals(Optional.of(fresh), state.questAtSlot(0));
    }

    @Test
    public void replaceQuestIfPresentIgnoresQuestsNoLongerInTheState() {
        GeneratedQuest original = quest(0);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(original));
        GeneratedQuest rerolled = quest(0);
        state.replaceQuestAtSlot(0, rerolled);

        assertFalse(state.replaceQuestIfPresent(original.withProgress(3, false)));
        assertEquals(Optional.of(rerolled), state.questAtSlot(0));
        assertTrue(state.replaceQuestIfPresent(rerolled.withProgress(3, false)));
        assertEquals(3, state.questAtSlot(0).orElseThrow().progress());
        assertTrue(state.removeQuest(rerolled.instanceId()));
        assertTrue(state.questAtSlot(0).isEmpty());
    }

    @Test
    public void updateQuestSkipsMissingQuestsAndRejectedUpdates() {
        GeneratedQuest quest = quest(0);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(quest));
        AtomicInteger applied = new AtomicInteger();

        assertTrue(state.updateQuest(UUID.randomUUID(), current -> Optional.of(current.withProgress(1, false)), change -> applied.incrementAndGet()).isEmpty());
        assertTrue(state.updateQuest(quest.instanceId(), current -> Optional.empty(), change -> applied.incrementAndGet()).isEmpty());
        PlayerQuestState.QuestChange change = state.updateQuest(quest.instanceId(), current -> Optional.of(current.withProgress(4, false)), ignored -> applied.incrementAndGet()).orElseThrow();

        assertEquals(1, applied.get());
        assertEquals(quest, change.previous());
        assertEquals(4, change.updated().progress());
        assertEquals(4, state.questAtSlot(0).orElseThrow().progress());
    }

    @Test
    public void onAppliedSeesThePublishedChangeAndMayUpdateTheStateAgain() {
        GeneratedQuest quest = quest(0);
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", List.of(quest));

        state.updateQuest(quest.instanceId(), current -> Optional.of(current.withProgress(GOAL, true)), change -> {
            assertTrue(state.questAtSlot(0).orElseThrow().completed());
            state.setDifficultyCompletions("easy", 1);
        });

        assertTrue(state.questAtSlot(0).orElseThrow().completed());
        assertEquals(1, state.difficultyCompletions("easy"));
    }

    @Test
    public void concurrentWritersAndReadersLoseNoUpdates() throws Exception {
        int writers = 8;
        int incrementsPerWriter = 2_000;
        List<GeneratedQuest> quests = new ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            quests.add(quest(slot));
        }
        PlayerQuestState state = new PlayerQuestState(UUID.randomUUID(), "daily", quests);
        ExecutorService executor = Executors.newFixedThreadPool(writers + 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        List<Future<?>> writes = new ArrayList<>();
        try {
            for (int writer = 0; writer < writers; writer++) {
                int writerId = writer;
                writes.add(executor.submit(() -> {
                    start.await();
                    for (int increment = 0; increment < incrementsPerWriter; increment++) {
                        GeneratedQuest target = quests.get((writerId + increment) % quests.size());
                        state.updateQuest(target.instanceId(), current -> Optional.of(current.withProgress(current.progress() + 1, false)), change -> {
                        });
                        state.markMilestoneExecuted("writer-" + writerId, increment);
                        state.setDifficultyCompletions("writer-" + writerId, increment + 1);
                    }
                    return null;
                }));
            }
            List<Future<?>> reads = new ArrayList<>();
            for (int reader = 0; reader < 2; reader++) {
                reads.add(executor.submit(() -> {
                    start.await();
                    while (writing.get()) {
                        int previousSlot = -1;
                        for (GeneratedQuest quest : state.quests()) {
                            assertTrue(quest.slotIndex() > previousSlot, "quests are sorted by slot");
                            previousSlot = quest.slotIndex();
                        }
                        state.difficultyCompletions().forEach((key, value) -> assertTrue(value > 0));
                        state.executedMilestones().forEach(key -> assertTrue(key.startsWith("writer-")));
                        state.questsCompleted();
                        state.hasActiveQuest(QuestType.of("KILL_MOB"));
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> write : writes) {
                write.get(30, TimeUnit.SECONDS);
            }
            writing.set(false);
            for (Future<?> read : reads) {
                read.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        int totalProgress = state.quests().stream().mapToInt(GeneratedQuest::progress).sum();
        assertEquals(writers * incrementsPerWriter, totalProgress);
        assertEquals(writers * incrementsPerWriter, state.executedMilestones().size());
        assertEquals(writers * incrementsPerWriter, state.questsCompleted());
        assertEquals(quests.size(), state.quests().size());
    }

    private static GeneratedQuest quest(int slot) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "definition-" + slot,
                QuestType.of("KILL_MOB"),
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                slot,
                GOAL,
                0,
                false
        );
    }
}
