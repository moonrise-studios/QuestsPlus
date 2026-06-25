package gg.moonrise.quests.model;

public record QuestSelectionResult(
        PlayerQuestState state,
        QuestSelectionStatus status,
        int rerollsUsed,
        int rerollsLimit
) {

    public QuestSelectionResult(PlayerQuestState state, boolean generated) {
        this(state, generated ? QuestSelectionStatus.SELECTED : QuestSelectionStatus.NO_AVAILABLE_QUEST, 0, 0);
    }

    public boolean generated() {
        return status == QuestSelectionStatus.SELECTED || status == QuestSelectionStatus.REROLLED;
    }

    public boolean rerolled() {
        return status == QuestSelectionStatus.REROLLED;
    }
}
