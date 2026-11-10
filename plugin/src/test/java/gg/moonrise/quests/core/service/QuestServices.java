package gg.moonrise.quests.core.service;

import gg.moonrise.quests.infrastructure.persistence.QuestRepository;

/**
 * The player quest services wired together the way the Spring context wires them, for tests that exercise them
 * against mocked repositories and collaborators.
 */
public record QuestServices(
        QuestService state,
        QuestSlotService slots,
        QuestUsageService usage,
        QuestSelectionService selection,
        QuestProgressService progress,
        QuestRewardService rewards,
        QuestTextService text
) {

    public static QuestServices create(
            ConfigProvider configProvider,
            QuestDefinitionService definitionService,
            QuestRepository repository,
            QuestStreakService streakService,
            GlobalQuestService globalQuestService,
            QuestProgressIndicatorService progressIndicatorService
    ) {
        QuestSlotService slots = new QuestSlotService(configProvider);
        QuestTextService text = new QuestTextService(configProvider, definitionService, slots);
        QuestRewardService rewards = new QuestRewardService(configProvider, definitionService, repository, streakService, slots, text);
        QuestService state = new QuestService(repository, streakService, slots, rewards);
        QuestUsageService usage = new QuestUsageService(configProvider, repository, state, slots);
        QuestSelectionService selection = new QuestSelectionService(definitionService, repository, state, usage, slots);
        QuestProgressService progress = new QuestProgressService(definitionService, repository, globalQuestService, progressIndicatorService, state, slots, rewards);
        return new QuestServices(state, slots, usage, selection, progress, rewards, text);
    }
}
