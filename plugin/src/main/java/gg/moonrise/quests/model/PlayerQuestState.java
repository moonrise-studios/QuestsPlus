package gg.moonrise.quests.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestType;

public class PlayerQuestState {

    private final UUID playerId;
    private final String resetKey;
    private final List<GeneratedQuest> quests;
    private final Map<String, Integer> difficultyCompletions;
    private final Set<String> executedMilestones;

    public PlayerQuestState(UUID playerId, String resetKey, List<GeneratedQuest> quests) {
        this(playerId, resetKey, quests, Map.of(), Set.of());
    }

    public PlayerQuestState(UUID playerId, String resetKey, List<GeneratedQuest> quests, Map<String, Integer> difficultyCompletions, Set<String> executedMilestones) {
        this.playerId = playerId;
        this.resetKey = resetKey;
        this.quests = new ArrayList<>(quests);
        this.difficultyCompletions = new LinkedHashMap<>(difficultyCompletions);
        this.executedMilestones = new LinkedHashSet<>(executedMilestones);
    }

    public UUID playerId() {
        return playerId;
    }

    public String resetKey() {
        return resetKey;
    }

    public List<GeneratedQuest> quests() {
        return quests.stream()
                .sorted(java.util.Comparator.comparingInt(GeneratedQuest::slotIndex))
                .toList();
    }

    public int questsCompleted() {
        return difficultyCompletions.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> difficultyCompletions() {
        return Map.copyOf(difficultyCompletions);
    }

    public int difficultyCompletions(String difficultyId) {
        return difficultyCompletions.getOrDefault(difficultyId, 0);
    }

    public Set<String> executedMilestones() {
        return Set.copyOf(executedMilestones);
    }

    public boolean hasExecutedMilestone(String difficultyId, int completed) {
        return executedMilestones.contains(milestoneKey(difficultyId, completed));
    }

    public boolean hasActiveQuest(QuestType type) {
        for (GeneratedQuest quest : quests) {
            if (!quest.completed() && quest.type().equals(type)) {
                return true;
            }
        }
        return false;
    }

    public GeneratedQuest questAtSlot(int slotIndex) {
        for (GeneratedQuest quest : quests) {
            if (quest.slotIndex() == slotIndex) {
                return quest;
            }
        }
        return null;
    }

    public boolean hasQuestAtSlot(int slotIndex) {
        return questAtSlot(slotIndex) != null;
    }

    public void setDifficultyCompletions(String difficultyId, int completed) {
        difficultyCompletions.put(difficultyId, completed);
    }

    public void markMilestoneExecuted(String difficultyId, int completed) {
        executedMilestones.add(milestoneKey(difficultyId, completed));
    }

    public void replaceQuest(GeneratedQuest quest) {
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).instanceId().equals(quest.instanceId())) {
                quests.set(index, quest);
                return;
            }
        }
        quests.add(quest);
    }

    public void replaceQuestAtSlot(int slotIndex, GeneratedQuest quest) {
        for (int index = 0; index < quests.size(); index++) {
            if (quests.get(index).slotIndex() == slotIndex) {
                quests.set(index, quest);
                return;
            }
        }
        quests.add(quest);
    }

    public static String milestoneKey(String difficultyId, int completed) {
        return difficultyId + ":" + completed;
    }
}
