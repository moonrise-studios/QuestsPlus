package gg.moonrise.quests.application.quest;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestSelectionResult;
import gg.moonrise.quests.domain.model.QuestSelectionStatus;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Fills an empty quest slot with a quest of the chosen difficulty, or rerolls the quest in a slot.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestSelectionService {

    private final QuestDefinitionService definitionService;
    private final QuestRepository questRepository;
    private final QuestService questService;
    private final QuestUsageService usageService;
    private final QuestSlotService slotService;

    public CompletableFuture<QuestSelectionResult> selectQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        UUID playerId = player.getUniqueId();
        int rerollLimit = slotService.rerollLimit(player);
        if (!slotService.canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return questService.ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
        }

        return questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> {
                    if (state.hasQuestAtSlot(slotIndex)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.SLOT_FILLED, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }
                    if (!difficultyRequirementsMet(normalizedDifficulty, state)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REQUIREMENTS_NOT_MET, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }

                    Optional<GeneratedQuest> generatedQuest = generateQuestForDifficultySlot(playerId, resetKey, slotIndex, normalizedDifficulty, state, null);
                    if (generatedQuest.isEmpty()) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }
                    GeneratedQuest generated = generatedQuest.get();
                    // A concurrent selection may have filled the slot while this quest was generated.
                    if (!state.putQuestIfSlotEmpty(generated)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.SLOT_FILLED, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
                    }

                    return questRepository.saveGeneratedQuests(List.of(generated))
                            .whenComplete((unused, throwable) -> {
                                if (throwable == null) {
                                    return;
                                }
                                // The quest was never stored, so take it back out of the cached state; otherwise the
                                // slot would look filled and its progress updates would hit no row.
                                state.removeQuest(generated.instanceId());
                            })
                            .thenApply(unused -> new QuestSelectionResult(state, QuestSelectionStatus.SELECTED, usageService.cachedRerollsUsed(playerId, resetKey), rerollLimit));
                });
    }

    public CompletableFuture<QuestSelectionResult> rerollQuestDifficulty(Player player, String resetKey, int slotIndex, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        UUID playerId = player.getUniqueId();
        int limit = slotService.rerollLimit(player);
        if (!slotService.canAccessSlot(player, slotIndex) || normalizedDifficulty.isBlank()) {
            return questService.ensurePlayerStateAsync(player, resetKey).thenApply(state -> new QuestSelectionResult(state, QuestSelectionStatus.INVALID, usageService.cachedRerollsUsed(playerId, resetKey), limit));
        }

        return questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> usageService.ensureRerollUsageAsync(playerId, resetKey).thenCompose(used -> {
                    Optional<GeneratedQuest> existingQuest = state.questAtSlot(slotIndex);
                    if (existingQuest.isEmpty()) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.INVALID, used, limit));
                    }
                    GeneratedQuest existing = existingQuest.get();
                    if (existing.completed()) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.COMPLETED, used, limit));
                    }
                    if (!difficultyRequirementsMet(normalizedDifficulty, state)) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REQUIREMENTS_NOT_MET, used, limit));
                    }
                    if (used >= limit) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_REROLLS, used, limit));
                    }

                    Optional<GeneratedQuest> generatedQuest = generateQuestForDifficultySlot(playerId, resetKey, slotIndex, normalizedDifficulty, state, existing);
                    if (generatedQuest.isEmpty()) {
                        return CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_AVAILABLE_QUEST, used, limit));
                    }
                    GeneratedQuest generated = generatedQuest.get();

                    // The cached checks above can be stale (e.g. a double click submits two rerolls before either
                    // finishes), so the repository re-checks the limit and the old row inside its transaction.
                    return questRepository.replaceQuestAndIncrementRerolls(existing.instanceId(), generated, limit)
                            .thenCompose(outcome -> applyRerollOutcome(player, resetKey, state, slotIndex, existing, generated, limit, outcome));
                }));
    }

    private CompletableFuture<QuestSelectionResult> applyRerollOutcome(Player player, String resetKey, PlayerQuestState state, int slotIndex,
                                                                       GeneratedQuest existing, GeneratedQuest generated, int limit,
                                                                       QuestRepository.RerollOutcome outcome) {
        UUID playerId = player.getUniqueId();
        return switch (outcome) {
            case QuestRepository.RerollOutcome.Rerolled rerolled -> {
                state.replaceQuestAtSlot(slotIndex, generated);
                usageService.recordRerollsUsed(playerId, resetKey, rerolled.used());
                yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.REROLLED, rerolled.used(), limit));
            }
            case QuestRepository.RerollOutcome.LimitReached limitReached -> {
                usageService.recordRerollsUsed(playerId, resetKey, limitReached.used());
                yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.NO_REROLLS, limitReached.used(), limit));
            }
            case QuestRepository.RerollOutcome.QuestMissing missing -> {
                int used = usageService.cachedRerollsUsed(playerId, resetKey);
                boolean stale = state.questAtSlot(slotIndex)
                        .filter(current -> current.instanceId().equals(existing.instanceId()))
                        .isPresent();
                if (!stale) {
                    // Another request already replaced this quest; the cached state shows its result.
                    yield CompletableFuture.completedFuture(new QuestSelectionResult(state, QuestSelectionStatus.INVALID, used, limit));
                }
                // The row disappeared behind the cache's back, so reload before showing the menu again.
                questService.invalidateState(playerId);
                yield questService.ensurePlayerStateAsync(player, resetKey)
                        .thenApply(fresh -> new QuestSelectionResult(fresh, QuestSelectionStatus.INVALID, used, limit));
            }
        };
    }

    private boolean difficultyRequirementsMet(String difficultyId, PlayerQuestState state) {
        return definitionService.difficulty(difficultyId)
                .map(difficulty -> difficulty.requirementsMet(state))
                .orElse(true);
    }

    private Optional<GeneratedQuest> generateQuestForDifficultySlot(UUID playerId, String resetKey, int slotIndex, String difficultyId,
                                                                    PlayerQuestState state, @Nullable GeneratedQuest replacing) {
        List<QuestDefinition> pool = definitionService.enabledDefinitions().stream()
                .filter(definition -> definition.difficultyId().equals(difficultyId))
                .toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        Set<String> activeDefinitionIds = state.quests().stream()
                .filter(quest -> replacing == null || !quest.instanceId().equals(replacing.instanceId()))
                .map(GeneratedQuest::definitionId)
                .collect(Collectors.toUnmodifiableSet());
        List<QuestDefinition> unused = pool.stream()
                .filter(definition -> !activeDefinitionIds.contains(definition.id()))
                .toList();
        List<QuestDefinition> candidates = unused.isEmpty() ? pool : unused;
        if (replacing != null && candidates.size() > 1) {
            List<QuestDefinition> withoutCurrent = candidates.stream()
                    .filter(definition -> !definition.id().equals(replacing.definitionId()))
                    .toList();
            if (!withoutCurrent.isEmpty()) {
                candidates = withoutCurrent;
            }
        }
        QuestDefinition definition = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Map<String, String> variables = definitionService.resolveVariables(definition);
        return Optional.of(definitionService.handler(definition.type())
                .createGeneratedQuest(definition, playerId, resetKey, variables)
                .withSlotIndex(slotIndex)
                .withPremium(slotService.isPremiumSlot(slotIndex)));
    }
}
