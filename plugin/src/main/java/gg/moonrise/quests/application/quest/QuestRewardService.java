package gg.moonrise.quests.application.quest;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.PremiumQuestsFile;
import gg.moonrise.quests.config.Rewards;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestCompletionStats;
import gg.moonrise.quests.domain.model.QuestDifficulty;
import gg.moonrise.quests.domain.model.QuestMilestone;
import gg.moonrise.quests.domain.model.QuestMilestoneClaim;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;
import gg.moonrise.quests.infrastructure.persistence.QuestRepository;
import gg.moonrise.quests.application.streak.QuestStreakService;
import gg.moonrise.quests.core.service.ConfigProvider;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs what follows a stored quest completion: completion stats, difficulty milestones, reward commands, the
 * completion message and the streak evaluation.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestRewardService {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestRepository questRepository;
    private final QuestStreakService streakService;
    private final QuestSlotService slotService;
    private final QuestTextService textService;

    public boolean questMilestonesEnabled() {
        return configProvider.get().isQuestMilestonesEnabled();
    }

    /**
     * Runs the completion pipeline for a quest whose progress row is already stored as completed. Because the
     * completion is committed, the stats, milestones and reward commands must happen even if the player has
     * logged out meanwhile; only the chat message and the streak evaluation need them online. A streak missed
     * that way is evaluated on the player's next join (see {@link QuestService#ensurePlayerStateAsync(Player, String)}).
     */
    public void completeQuest(Player player, PlayerQuestState state, GeneratedQuest quest) {
        recordCompletionStats(player, state, quest);
        runOnEntityOrRetired(player, () -> {
            if (player.isOnline()) {
                configProvider.get().getMessages().getQuestCompleted().send(player, Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())));
            }
            // Already on the player's entity thread, so the commands run directly.
            dispatchConsoleCommands(player.getUniqueId(), resolvedRewardCommands(player, quest, slotService.canAccessQuest(player, quest)));
            evaluateStreak(player, state);
        }, () -> {
            // Progress is only credited to quests the player can access, so a premium quest that got completed
            // was accessible; the permission is not re-checked because it may no longer be readable offline.
            dispatchCommittedRewardCommands(player, resolvedRewardCommands(player, quest, quest.premium()));
        });
    }

    /**
     * Claims every milestone the player's completion counts already reached but that was never executed, then
     * runs its reward commands and messages.
     */
    public CompletableFuture<Void> claimRetroactiveMilestones(Player player, PlayerQuestState state) {
        if (!questMilestonesEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return questRepository.claimEligibleMilestones(player.getUniqueId(), state.difficultyCompletions(), definitionService.difficulties())
                .thenAccept(claims -> {
                    if (claims.isEmpty()) {
                        return;
                    }
                    state.markMilestonesExecuted(claims.stream()
                            .map(claim -> PlayerQuestState.milestoneKey(claim.difficultyId(), claim.milestone().completed()))
                            .toList());
                    handleMilestoneClaimRewards(player, claims);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to retroactively claim QuestsPlus milestones for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private List<String> resolvedRewardCommands(Player player, GeneratedQuest quest, boolean premiumRewardsAccessible) {
        List<String> commands = new ArrayList<>();
        for (String command : rewardCommands(quest, premiumRewardsAccessible)) {
            commands.add(textService.replaceQuestRewardTokens(command, quest, player));
        }
        return commands;
    }

    private void dispatchConsoleCommands(UUID playerId, List<String> commands) {
        for (String command : commands) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                log.error("Failed to run QuestsPlus reward command '{}' for {}.", command, playerId, exception);
            }
        }
    }

    private void evaluateStreak(Player player, PlayerQuestState state) {
        if (!streakService.isEnabled()) {
            return;
        }
        streakService.evaluateQuestCompletion(player, state, slotService.accessibleQuests(player, state))
                .exceptionally(throwable -> {
                    log.error("Failed to evaluate QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    /**
     * Runs {@code task} on the player's entity thread, or {@code retired} if the player is gone before it can run.
     * Paper returns {@code null} without calling the retired callback when the entity is already removed.
     */
    private void runOnEntityOrRetired(Player player, Runnable task, Runnable retired) {
        ScheduledTask scheduled = Scheduler.entity(player).run(ignored -> task.run(), retired);
        if (scheduled == null) {
            retired.run();
        }
    }

    private void recordCompletionStats(Player player, PlayerQuestState state, GeneratedQuest quest) {
        List<QuestMilestone> milestones = !questMilestonesEnabled() ? List.of() : definitionService.difficulty(quest.difficultyId())
                .map(QuestDifficulty::milestones)
                .orElse(List.of());
        questRepository.incrementCompletionStats(player.getUniqueId(), quest.difficultyId(), milestones)
                .thenAccept(stats -> {
                    state.setDifficultyCompletions(quest.difficultyId(), stats.difficultyCompleted());
                    state.markMilestonesExecuted(stats.newlyExecutedMilestones().stream()
                            .map(milestone -> PlayerQuestState.milestoneKey(milestone.difficultyId(), milestone.completed()))
                            .toList());
                    handleMilestoneRewards(player, quest, stats);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to persist QuestsPlus completion stats for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    private void handleMilestoneRewards(Player player, GeneratedQuest quest, QuestCompletionStats stats) {
        if (!questMilestonesEnabled() || stats.newlyExecutedMilestones().isEmpty()) {
            return;
        }

        List<String> commands = new ArrayList<>();
        for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
            for (String command : milestone.commands()) {
                commands.add(replaceMilestoneTokens(command, player, quest, stats, milestone));
            }
        }
        dispatchCommittedRewardCommands(player, commands);
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestMilestone milestone : stats.newlyExecutedMilestones()) {
                sendMilestoneCompletedMessage(player, quest, stats, milestone);
            }
        });
    }

    private void handleMilestoneClaimRewards(Player player, List<QuestMilestoneClaim> claims) {
        if (!questMilestonesEnabled() || claims.isEmpty()) {
            return;
        }

        List<String> commands = new ArrayList<>();
        for (QuestMilestoneClaim claim : claims) {
            for (String command : claim.milestone().commands()) {
                commands.add(replaceMilestoneClaimTokens(command, player, claim));
            }
        }
        dispatchCommittedRewardCommands(player, commands);
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }
            for (QuestMilestoneClaim claim : claims) {
                sendMilestoneClaimedMessage(player, claim);
            }
        });
    }

    /**
     * Runs reward commands whose claim is already committed. They must run even if the player has logged out
     * meanwhile, so they go to the global scheduler as console commands; their tokens only need the name and
     * UUID, which the caller resolved while the Player was at hand.
     */
    private void dispatchCommittedRewardCommands(Player player, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Scheduler.sync().run(task -> dispatchConsoleCommands(playerId, commands));
    }

    private void sendMilestoneCompletedMessage(Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        configProvider.get().getMessages().getMilestoneCompleted().send(
                player,
                Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())),
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", QuestTokens.stripTags(quest.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", quest.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(stats.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(milestone.completed())),
                Placeholder.unparsed("milestone_display_name", QuestTokens.stripTags(milestone.displayName()))
        );
    }

    private void sendMilestoneClaimedMessage(Player player, QuestMilestoneClaim claim) {
        configProvider.get().getMessages().getMilestoneClaimed().send(
                player,
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty", QuestTokens.stripTags(claim.difficultyDisplayName())),
                Placeholder.unparsed("difficulty_id", claim.difficultyId()),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(claim.difficultyCompleted())),
                Placeholder.unparsed("milestone_completed", QuestNumberFormatter.format(claim.milestone().completed())),
                Placeholder.unparsed("milestone_display_name", QuestTokens.stripTags(claim.milestone().displayName()))
        );
    }

    private String replaceMilestoneTokens(String template, Player player, GeneratedQuest quest, QuestCompletionStats stats, QuestMilestone milestone) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", quest.difficultyDisplayName());
        values.put("difficulty_id", quest.difficultyId());
        values.put("completed", Integer.toString(stats.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(milestone.completed()));
        return QuestTokens.apply(template, values);
    }

    private String replaceMilestoneClaimTokens(String template, Player player, QuestMilestoneClaim claim) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("difficulty", claim.difficultyDisplayName());
        values.put("difficulty_id", claim.difficultyId());
        values.put("completed", Integer.toString(claim.difficultyCompleted()));
        values.put("milestone_completed", Integer.toString(claim.milestone().completed()));
        return QuestTokens.apply(template, values);
    }

    private List<String> rewardCommands(GeneratedQuest quest, boolean premiumRewardsAccessible) {
        List<String> commands = new ArrayList<>();
        definitionService.difficulty(quest.difficultyId())
                .flatMap(difficulty -> randomDifficultyRewardCommand(difficulty.rewardCommands()))
                .ifPresent(commands::add);
        if (quest.premium() && premiumRewardsAccessible) {
            PremiumQuestsFile premium = configProvider.get().getPremiumQuests();
            Map<String, Rewards> rewards = premium == null || premium.getRewards() == null ? Map.of() : premium.getRewards();
            Rewards bonus = rewards.get(quest.difficultyId());
            if (bonus == null) {
                bonus = rewards.get(QuestNames.normalize(quest.difficultyId()));
            }
            if (bonus != null && bonus.getCommands() != null) {
                randomDifficultyRewardCommand(bonus.getCommands()).ifPresent(commands::add);
            }
        }
        definitionService.definition(quest)
                .map(QuestDefinition::rewardCommands)
                .ifPresent(commands::addAll);
        return commands;
    }

    private Optional<String> randomDifficultyRewardCommand(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(ThreadLocalRandom.current().nextInt(commands.size())));
    }
}
