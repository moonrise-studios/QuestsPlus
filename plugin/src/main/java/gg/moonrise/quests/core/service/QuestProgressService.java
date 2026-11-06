package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.sdk.event.QuestProgressEvent;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestProgressResult;
import gg.moonrise.quests.sdk.model.QuestType;
import gg.moonrise.quests.util.QuestNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Credits progress to a player's daily quests (and the active global quest), stores it, and hands newly completed
 * quests to {@link QuestRewardService}.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestProgressService {

    private final QuestDefinitionService definitionService;
    private final QuestRepository questRepository;
    private final GlobalQuestService globalQuestService;
    private final QuestProgressIndicatorService progressIndicatorService;
    private final QuestService questService;
    private final QuestSlotService slotService;
    private final QuestRewardService rewardService;

    public boolean hasCachedGlobalQuest(QuestType type) {
        return globalQuestService.cachedActiveState()
                .filter(state -> !state.quest().completed() && state.quest().type().equals(type))
                .isPresent();
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, Predicate<GeneratedQuest> matcher) {
        return progressMatching(player, type, resetKey, 1, matcher);
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, int amount, Predicate<GeneratedQuest> matcher) {
        return progressMatching(player, type, resetKey, amount, matcher, QuestProgressEvent.Cause.EVENT);
    }

    public List<QuestProgressResult> progressMatching(Player player, QuestType type, String resetKey, int amount, Predicate<GeneratedQuest> matcher, QuestProgressEvent.Cause cause) {
        if (amount <= 0) {
            return List.of();
        }

        Optional<PlayerQuestState> cached = questService.cachedState(player.getUniqueId(), resetKey);
        if (cached.isEmpty()) {
            questService.ensurePlayerStateAsync(player, resetKey)
                    .thenRun(() -> Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            progressMatching(player, type, resetKey, amount, matcher, cause);
                        }
                    }))
                    .exceptionally(throwable -> {
                        log.error("Failed to load QuestsPlus player state before applying {} progress for {}.", type.key(), player.getUniqueId(), throwable);
                        return null;
                    });
            return List.of();
        }

        globalQuestService.progressMatching(player, type, amount, matcher, cause);

        return applyProgress(player, cached.get(), quest -> quest.type().equals(type) && matcher.test(quest), amount, cause, "progress");
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, String resetKey, int amount, String definitionId) {
        if (amount <= 0) {
            return List.of();
        }

        String normalizedDefinitionId = QuestNames.normalize(definitionId);
        if (normalizedDefinitionId.isBlank()) {
            return List.of();
        }

        return questService.cachedState(player.getUniqueId(), resetKey)
                .map(state -> applyProgress(player, state, quest -> quest.definitionId().equals(normalizedDefinitionId), amount, QuestProgressEvent.Cause.COMMAND, "command progress"))
                .orElse(List.of());
    }

    public List<QuestProgressResult> progressAdminGoal(Player player, String resetKey, int amount, QuestType type) {
        if (amount <= 0 || type == null) {
            return List.of();
        }

        return questService.cachedState(player.getUniqueId(), resetKey)
                .map(state -> applyProgress(player, state, quest -> quest.type().equals(type), amount, QuestProgressEvent.Cause.COMMAND, "command progress"))
                .orElse(List.of());
    }

    public CompletableFuture<Integer> completeDailyQuests(Player player, String resetKey) {
        return questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> runOnEntity(player, () -> {
                    List<CompletableFuture<Boolean>> updates = new ArrayList<>();
                    for (GeneratedQuest quest : state.quests()) {
                        if (quest.completed() || !slotService.canAccessQuest(player, quest)) {
                            continue;
                        }
                        // Re-checked atomically: progress from another thread may have completed it meanwhile.
                        state.updateQuest(
                                quest.instanceId(),
                                current -> current.completed() ? Optional.empty() : Optional.of(current.withProgress(current.goalAmount(), true)),
                                change -> updates.add(persistProgress(player, state, change.previous(), change.updated(), "admin completion"))
                        ).ifPresent(change -> progressIndicatorService.showPersonal(player, change.updated(), change.previous().progress()));
                    }

                    if (updates.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }
                    return CompletableFuture.allOf(updates.toArray(CompletableFuture[]::new))
                            .thenApply(unused -> (int) updates.stream().filter(CompletableFuture::join).count());
                }));
    }

    /**
     * Credits {@code amount} to every active, accessible quest matching {@code filter}. The QuestProgressEvent is
     * fired outside any state update, because third-party listeners may call back into QuestsPlus from other
     * threads; each approved amount is then applied atomically only if the same quest instance is still active,
     * clamped against its current progress. The database write is queued inside that atomic update so writes for
     * one quest reach the database in the order the cached progress changed.
     */
    private List<QuestProgressResult> applyProgress(Player player, PlayerQuestState state, Predicate<GeneratedQuest> filter, int amount,
                                                    QuestProgressEvent.Cause cause, String context) {
        List<GeneratedQuest> candidates = state.quests().stream()
                .filter(quest -> !quest.completed() && slotService.canAccessQuest(player, quest) && filter.test(quest))
                .toList();

        List<QuestProgressResult> results = new ArrayList<>();
        for (GeneratedQuest candidate : candidates) {
            int approvedAmount = approveProgress(player, candidate, amount, cause);
            if (approvedAmount <= 0) {
                continue;
            }

            // The quest may have been rerolled, reset, completed or advanced while listeners ran.
            state.updateQuest(
                    candidate.instanceId(),
                    current -> current.completed() ? Optional.empty() : Optional.of(withAddedProgress(current, approvedAmount)),
                    change -> persistProgress(player, state, change.previous(), change.updated(), context)
            ).ifPresent(change -> {
                results.add(new QuestProgressResult(change.updated(), change.updated().completed()));
                progressIndicatorService.showPersonal(player, change.updated(), change.previous().progress());
            });
        }
        return results;
    }

    private GeneratedQuest withAddedProgress(GeneratedQuest quest, int amount) {
        int updatedProgress = clampedProgress(quest.progress(), amount, quest.goalAmount());
        return quest.withProgress(updatedProgress, updatedProgress >= quest.goalAmount());
    }

    private CompletableFuture<Boolean> persistProgress(Player player, PlayerQuestState state, GeneratedQuest previous, GeneratedQuest updated, String context) {
        return questRepository.updateProgress(updated)
                .thenApply(outcome -> switch (outcome) {
                    case QuestRepository.ProgressUpdateOutcome.Updated stored -> {
                        runCompletionIfNewlyCompleted(player, state, previous, updated);
                        yield true;
                    }
                    case QuestRepository.ProgressUpdateOutcome.QuestMissing missing -> {
                        state.replaceQuestIfPresent(previous);
                        log.warn("Skipped QuestsPlus {} side effects for quest {} because the progress row was not updated.", context, updated.instanceId());
                        yield false;
                    }
                })
                .exceptionally(throwable -> {
                    state.replaceQuestIfPresent(previous);
                    log.error("Failed to persist QuestsPlus {} for quest {}.", context, updated.instanceId(), throwable);
                    return false;
                });
    }

    private void runCompletionIfNewlyCompleted(Player player, PlayerQuestState state, GeneratedQuest previous, GeneratedQuest updated) {
        if (updated.completed() && !previous.completed()) {
            try {
                rewardService.completeQuest(player, state, updated);
            } catch (RuntimeException exception) {
                // The row is stored, so a failing side effect must not roll the cached quest back.
                log.error("Failed to run QuestsPlus completion side effects for quest {}.", updated.instanceId(), exception);
            }
        }
    }

    private <T> CompletableFuture<T> runOnEntity(Player player, Supplier<CompletableFuture<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            Scheduler.entity(player).run(task -> {
                try {
                    supplier.get().whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            future.complete(result);
                        } else {
                            future.completeExceptionally(throwable);
                        }
                    });
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            }, () -> future.completeExceptionally(new IllegalStateException("Player is not available")));
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    private int clampedProgress(int progress, int amount, int goalAmount) {
        return Math.clamp((long) progress + amount, 0, Math.max(0, goalAmount));
    }

    private int approveProgress(Player player, GeneratedQuest quest, int amount, QuestProgressEvent.Cause cause) {
        QuestDefinition definition = definitionService.definition(quest).orElse(null);
        if (definition == null) {
            log.warn("Skipping QuestsPlus progress for generated quest {} because definition '{}' is not loaded.", quest.instanceId(), quest.definitionId());
            return 0;
        }

        QuestProgressEvent event = new QuestProgressEvent(player, definition, quest, cause == null ? QuestProgressEvent.Cause.UNKNOWN : cause, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return 0;
        }
        return event.amount();
    }
}
