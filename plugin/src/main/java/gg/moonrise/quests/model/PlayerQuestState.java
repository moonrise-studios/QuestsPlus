package gg.moonrise.quests.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player's quests and completion stats for one reset window, shared between the SQL thread and entity threads.
 * <p>
 * The mutable data lives in an immutable {@link Snapshot} that is swapped atomically, so readers never need a lock
 * and never see a half-applied change. Writers copy the snapshot under a private lock; every check-then-act
 * sequence a caller needs is offered as one method here, so callers never have to lock the state themselves.
 */
public final class PlayerQuestState {

    private static final Comparator<GeneratedQuest> BY_SLOT = Comparator.comparingInt(GeneratedQuest::slotIndex);

    private final UUID playerId;
    private final String resetKey;
    private final Object writeLock = new Object();
    private volatile Snapshot snapshot;

    public PlayerQuestState(@NotNull UUID playerId, @NotNull String resetKey, @NotNull List<GeneratedQuest> quests) {
        this(playerId, resetKey, quests, Map.of(), Set.of());
    }

    public PlayerQuestState(@NotNull UUID playerId, @NotNull String resetKey, @NotNull List<GeneratedQuest> quests,
                            @NotNull Map<String, Integer> difficultyCompletions, @NotNull Set<String> executedMilestones) {
        this.playerId = playerId;
        this.resetKey = resetKey;
        this.snapshot = new Snapshot(sortedBySlot(quests), copyOf(difficultyCompletions), copyOf(executedMilestones));
    }

    public UUID playerId() {
        return playerId;
    }

    public String resetKey() {
        return resetKey;
    }

    /**
     * Returns the quests sorted by slot index. The list is an immutable snapshot and never changes afterwards.
     */
    public List<GeneratedQuest> quests() {
        return snapshot.quests();
    }

    public int questsCompleted() {
        int total = 0;
        for (int completed : snapshot.difficultyCompletions().values()) {
            total += completed;
        }
        return total;
    }

    public Map<String, Integer> difficultyCompletions() {
        return snapshot.difficultyCompletions();
    }

    public int difficultyCompletions(String difficultyId) {
        return snapshot.difficultyCompletions().getOrDefault(difficultyId, 0);
    }

    public Set<String> executedMilestones() {
        return snapshot.executedMilestones();
    }

    public boolean hasExecutedMilestone(String difficultyId, int completed) {
        return snapshot.executedMilestones().contains(milestoneKey(difficultyId, completed));
    }

    public boolean hasActiveQuest(QuestType type) {
        for (GeneratedQuest quest : snapshot.quests()) {
            if (!quest.completed() && quest.type().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public @Nullable GeneratedQuest questAtSlot(int slotIndex) {
        return findAtSlot(snapshot.quests(), slotIndex);
    }

    public boolean hasQuestAtSlot(int slotIndex) {
        return questAtSlot(slotIndex) != null;
    }

    public void setDifficultyCompletions(@NotNull String difficultyId, int completed) {
        synchronized (writeLock) {
            Map<String, Integer> completions = new LinkedHashMap<>(snapshot.difficultyCompletions());
            completions.put(difficultyId, completed);
            snapshot = new Snapshot(snapshot.quests(), Collections.unmodifiableMap(completions), snapshot.executedMilestones());
        }
    }

    public void markMilestoneExecuted(@NotNull String difficultyId, int completed) {
        markMilestonesExecuted(List.of(milestoneKey(difficultyId, completed)));
    }

    /**
     * Marks several milestones (as {@link #milestoneKey(String, int)} keys) executed in one atomic change.
     */
    public void markMilestonesExecuted(@NotNull Collection<String> milestoneKeys) {
        if (milestoneKeys.isEmpty()) {
            return;
        }
        synchronized (writeLock) {
            Set<String> milestones = new LinkedHashSet<>(snapshot.executedMilestones());
            milestones.addAll(milestoneKeys);
            snapshot = new Snapshot(snapshot.quests(), snapshot.difficultyCompletions(), Collections.unmodifiableSet(milestones));
        }
    }

    /**
     * Replaces the quest with the same instance id, or adds the quest if there is none.
     */
    public void replaceQuest(@NotNull GeneratedQuest quest) {
        updateQuests(quests -> {
            int index = indexOfInstance(quests, quest.instanceId());
            if (index < 0) {
                quests.add(quest);
            } else {
                quests.set(index, quest);
            }
            return true;
        });
    }

    /**
     * Replaces the quest with the same instance id. Unlike {@link #replaceQuest(GeneratedQuest)}, nothing is added
     * if the quest is no longer part of this state (for example because it was rerolled meanwhile).
     *
     * @return whether a quest was replaced
     */
    public boolean replaceQuestIfPresent(@NotNull GeneratedQuest quest) {
        return updateQuests(quests -> {
            int index = indexOfInstance(quests, quest.instanceId());
            if (index < 0) {
                return false;
            }
            quests.set(index, quest);
            return true;
        });
    }

    public boolean removeQuest(@NotNull UUID instanceId) {
        return updateQuests(quests -> quests.removeIf(quest -> quest.instanceId().equals(instanceId)));
    }

    /**
     * Puts {@code quest} into its slot, replacing whatever quest was there before.
     */
    public void replaceQuestAtSlot(int slotIndex, @NotNull GeneratedQuest quest) {
        updateQuests(quests -> {
            int index = indexOfSlot(quests, slotIndex);
            if (index < 0) {
                quests.add(quest);
            } else {
                quests.set(index, quest);
            }
            return true;
        });
    }

    /**
     * Adds {@code quest} only if its slot is still empty.
     *
     * @return whether the quest was added
     */
    public boolean putQuestIfSlotEmpty(@NotNull GeneratedQuest quest) {
        return updateQuests(quests -> {
            if (indexOfSlot(quests, quest.slotIndex()) >= 0) {
                return false;
            }
            quests.add(quest);
            return true;
        });
    }

    /**
     * Atomically updates the quest with {@code instanceId}. {@code updater} receives the current quest and returns
     * its replacement, or empty to leave it unchanged; it runs exactly once under the write lock and must not
     * touch this state. {@code onApplied} runs right after the change is published but before any other writer can
     * change this state, so effects that must happen in the same order as the changes (such as queueing the
     * database write) can be started there. It may read and update this state again.
     *
     * @return the applied change, or empty if the quest is gone or {@code updater} returned empty
     */
    public Optional<QuestChange> updateQuest(@NotNull UUID instanceId,
                                             @NotNull Function<GeneratedQuest, Optional<GeneratedQuest>> updater,
                                             @NotNull Consumer<QuestChange> onApplied) {
        synchronized (writeLock) {
            List<GeneratedQuest> quests = snapshot.quests();
            int index = indexOfInstance(quests, instanceId);
            if (index < 0) {
                return Optional.empty();
            }
            GeneratedQuest previous = quests.get(index);
            Optional<GeneratedQuest> replacement = updater.apply(previous);
            if (replacement.isEmpty()) {
                return Optional.empty();
            }
            List<GeneratedQuest> updated = new ArrayList<>(quests);
            updated.set(index, replacement.get());
            snapshot = new Snapshot(sortedBySlot(updated), snapshot.difficultyCompletions(), snapshot.executedMilestones());
            QuestChange change = new QuestChange(previous, replacement.get());
            onApplied.accept(change);
            return Optional.of(change);
        }
    }

    public static String milestoneKey(String difficultyId, int completed) {
        return difficultyId + ":" + completed;
    }

    @Override
    public String toString() {
        return "PlayerQuestState[playerId=" + playerId + ", resetKey=" + resetKey + ", quests=" + snapshot.quests().size() + "]";
    }

    /**
     * Copies the quest list, lets {@code change} edit the copy and publishes it if {@code change} returns true.
     */
    private boolean updateQuests(QuestListChange change) {
        synchronized (writeLock) {
            List<GeneratedQuest> quests = new ArrayList<>(snapshot.quests());
            if (!change.apply(quests)) {
                return false;
            }
            snapshot = new Snapshot(sortedBySlot(quests), snapshot.difficultyCompletions(), snapshot.executedMilestones());
            return true;
        }
    }

    private static @Nullable GeneratedQuest findAtSlot(List<GeneratedQuest> quests, int slotIndex) {
        int index = indexOfSlot(quests, slotIndex);
        return index < 0 ? null : quests.get(index);
    }

    private static int indexOfSlot(List<GeneratedQuest> quests, int slotIndex) {
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).slotIndex() == slotIndex) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfInstance(List<GeneratedQuest> quests, UUID instanceId) {
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).instanceId().equals(instanceId)) {
                return index;
            }
        }
        return -1;
    }

    private static List<GeneratedQuest> sortedBySlot(List<GeneratedQuest> quests) {
        List<GeneratedQuest> sorted = new ArrayList<>(quests);
        sorted.sort(BY_SLOT);
        return Collections.unmodifiableList(sorted);
    }

    private static Map<String, Integer> copyOf(Map<String, Integer> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private static Set<String> copyOf(Set<String> set) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    /**
     * One quest update applied by {@link #updateQuest(UUID, Function, Consumer)}.
     */
    public record QuestChange(@NotNull GeneratedQuest previous, @NotNull GeneratedQuest updated) {
    }

    @FunctionalInterface
    private interface QuestListChange {
        boolean apply(List<GeneratedQuest> quests);
    }

    /**
     * The immutable data of one moment; {@code quests} is sorted by slot index.
     */
    private record Snapshot(List<GeneratedQuest> quests, Map<String, Integer> difficultyCompletions, Set<String> executedMilestones) {
    }
}
