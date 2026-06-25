package gg.moonrise.quests.sdk.model;

/**
 * Result of applying progress to a generated quest.
 *
 * @param quest the updated quest
 * @param completedNow whether the progress operation completed the quest
 */
public record QuestProgressResult(GeneratedQuest quest, boolean completedNow) {
}
