package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.model.QuestResetPaymentType;
import gg.moonrise.quests.model.QuestSelectionResult;
import gg.moonrise.quests.model.QuestSelectionStatus;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.ui.GlobalRewardPreviewUI;
import gg.moonrise.quests.ui.QuestDifficultyPickerUI;
import gg.moonrise.quests.ui.QuestResetPurchaseUI;
import gg.moonrise.quests.ui.QuestStreakConfirmUI;
import gg.moonrise.quests.ui.QuestStreakMenuUI;
import gg.moonrise.quests.ui.QuestMilestoneMenuUI;
import gg.moonrise.quests.ui.QuestMilestoneSelectorUI;
import gg.moonrise.quests.ui.QuestMenuUI;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestMenuService {

    private static final java.util.regex.Pattern QUEST_DESCRIPTION_TOKEN = java.util.regex.Pattern.compile("<quest_description>", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestService questService;
    private final QuestResetService resetService;
    private final QuestStreakService streakService;
    private final GlobalQuestService globalQuestService;
    private final QuestResetPurchaseService resetPurchaseService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void openDailyQuests(Player player) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> questService.ensureRerollUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenCompose(state -> questService.ensureQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenCompose(state -> activeGlobalQuestAsync().thenApply(globalState -> new DailyMenuState(state, globalState)))
                .thenAccept(menuState -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestMenuUI(player, this, menuState.questState(), menuState.globalState()).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus menu for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quests right now.");
                        }
                    });
                    return null;
                });
    }

    public void openMilestoneSelector(Player player) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestMilestoneSelectorUI(player, this, state).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus milestone selector for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest milestones right now.");
                        }
                    });
                    return null;
                });
    }

    public void openMilestones(Player player, String difficultyId) {
        String normalizedDifficulty = QuestNames.normalize(difficultyId);
        QuestDifficulty difficulty = definitionService.difficulty(normalizedDifficulty);
        if (difficulty == null || !difficulty.id().equals(normalizedDifficulty)) {
            player.sendRichMessage("<red>Unknown quest difficulty.");
            return;
        }

        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestMilestoneMenuUI(player, this, state, difficulty).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus milestones for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest milestones right now.");
                        }
                    });
                    return null;
                });
    }

    public void openStreaks(Player player) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(questState -> streakService.stateForMenu(player, resetKey).thenApply(streakState -> new StreakMenuState(questState, streakState)))
                .thenAccept(menuState -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestStreakMenuUI(player, this, menuState.questState(), menuState.streakState()).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus streak menu for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest streaks right now.");
                        }
                    });
                    return null;
                });
    }

    public void openStreakConfirmation(Player player) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(questState -> streakService.stateForMenu(player, resetKey).thenApply(streakState -> new StreakMenuState(questState, streakState)))
                .thenAccept(menuState -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestStreakConfirmUI(player, this, menuState.questState(), menuState.streakState()).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus streak confirmation for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest streak confirmation right now.");
                        }
                    });
                    return null;
                });
    }

    public void openQuestResetPurchase(Player player) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> questService.refreshQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    QuestResetEligibility eligibility = questService.resetEligibility(player, state);
                    if (!eligibility.eligible()) {
                        config().getMessages().getQuestResetNotReady().send(
                                player,
                                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required()))
                        );
                        new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState()).open();
                        return;
                    }
                    if (questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey) <= 0) {
                        sendResetLimitReached(player, eligibility);
                        new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState()).open();
                        return;
                    }
                    if (!resetPurchaseService.hasAvailablePaymentMethods()) {
                        sendPurchaseUnavailable(player, null, eligibility);
                        new QuestMenuUI(player, this, state, globalQuestService.cachedActiveState()).open();
                        return;
                    }
                    new QuestResetPurchaseUI(player, this, eligibility).open();
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus reset purchase menu for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest reset purchases right now.");
                        }
                    });
                    return null;
                });
    }

    public void openDifficultyPicker(Player player, int slotIndex) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (player.isOnline()) {
                        new QuestDifficultyPickerUI(player, this, slotIndex, false).open();
                    }
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus difficulty picker for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest difficulty picker right now.");
                        }
                    });
                    return null;
                });
    }

    public void openRerollDifficultyPicker(Player player, int slotIndex) {
        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> questService.ensureRerollUsageAsync(player.getUniqueId(), resetKey).thenApply(used -> state))
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    GeneratedQuest quest = state.questAtSlot(slotIndex);
                    if (quest == null) {
                        openDifficultyPicker(player, slotIndex);
                        return;
                    }
                    if (!questService.canAccessQuest(player, quest)) {
                        openDailyQuests(player);
                        return;
                    }
                    if (quest.completed()) {
                        config().getMessages().getCompletedQuestCannotReroll().send(player);
                        return;
                    }
                    if (questService.rerollsRemaining(player, resetKey) <= 0) {
                        config().getMessages().getNoRerollsRemaining().send(player);
                        return;
                    }
                    new QuestDifficultyPickerUI(player, this, slotIndex, true).open();
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to open QuestsPlus reroll difficulty picker for {}.", player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to open quest reroll picker right now.");
                        }
                    });
                    return null;
                });
    }

    public void selectDifficultyForSlot(Player player, int slotIndex, String difficultyId) {
        selectDifficultyForSlot(player, slotIndex, difficultyId, false);
    }

    public void selectDifficultyForSlot(Player player, int slotIndex, String difficultyId, boolean reroll) {
        String resetKey = resetService.currentResetKey();
        CompletableFuture<QuestSelectionResult> selection = reroll
                ? questService.rerollQuestDifficulty(player, resetKey, slotIndex, difficultyId)
                : questService.selectQuestDifficulty(player, resetKey, slotIndex, difficultyId);
        selection
                .thenAccept(result -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.status() == QuestSelectionStatus.NO_AVAILABLE_QUEST) {
                        String normalizedDifficulty = QuestNames.normalize(difficultyId);
                        Component difficultyName = withoutItalics(parse(difficultyDisplayName(normalizedDifficulty)));
                        config().getMessages().getNoAvailableQuestForDifficulty().send(
                                player,
                                Placeholder.unparsed("difficulty_id", normalizedDifficulty),
                                Placeholder.component("difficulty", difficultyName),
                                Placeholder.component("quest_difficulty", difficultyName)
                        );
                    } else if (result.status() == QuestSelectionStatus.REQUIREMENTS_NOT_MET) {
                        String normalizedDifficulty = QuestNames.normalize(difficultyId);
                        QuestDifficulty difficulty = definitionService.difficulty(normalizedDifficulty);
                        Component difficultyName = withoutItalics(parse(difficultyDisplayName(normalizedDifficulty)));
                        config().getMessages().getDifficultyRequirementsNotMet().send(
                                player,
                                Placeholder.unparsed("difficulty_id", normalizedDifficulty),
                                Placeholder.unparsed("requirements", difficultyRequirementsText(difficulty, result.state())),
                                Placeholder.component("difficulty", difficultyName),
                                Placeholder.component("quest_difficulty", difficultyName)
                        );
                    } else if (result.status() == QuestSelectionStatus.NO_REROLLS) {
                        config().getMessages().getNoRerollsRemaining().send(player);
                    } else if (result.status() == QuestSelectionStatus.COMPLETED) {
                        config().getMessages().getCompletedQuestCannotReroll().send(player);
                    } else if (result.status() == QuestSelectionStatus.REROLLED) {
                        config().getMessages().getQuestRerolled().send(
                                player,
                                Placeholder.unparsed("slot", QuestNumberFormatter.format(slotIndex + 1)),
                                Placeholder.unparsed("rerolls_remaining", QuestNumberFormatter.format(Math.max(0, result.rerollsLimit() - result.rerollsUsed())))
                        );
                    }
                    GlobalQuestState globalState = globalQuestService.cachedActiveState();
                    new QuestMenuUI(player, this, result.state(), globalState).open();
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to select QuestsPlus difficulty {} for {}.", difficultyId, player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        if (player.isOnline()) {
                            player.sendRichMessage("<red>Failed to select that quest difficulty right now.");
                        }
                    });
                    return null;
                });
    }

    public Config config() {
        return configProvider.get();
    }

    public int dailyQuestSlots() {
        return questService.dailyQuestSlots();
    }

    public int totalQuestSlots(Player player) {
        return questService.totalQuestSlots(player);
    }

    public int totalVisibleQuestSlots() {
        return questService.totalVisibleQuestSlots();
    }

    public boolean canAccessSlot(Player player, int slotIndex) {
        return questService.canAccessSlot(player, slotIndex);
    }

    public int globalQuestSlot() {
        return globalQuestService.menuSlot();
    }

    public CompletableFuture<GlobalQuestState> activeGlobalQuestAsync() {
        return globalQuestService.activeStateAsync();
    }

    public void openGlobalRewardPreview(Player player) {
        Scheduler.entity(player).run(task -> {
            if (player.isOnline()) {
                new GlobalRewardPreviewUI(player, this).open();
            }
        });
    }

    public ItemStack buildMenuItem(Config.MenuItem item) {
        return ItemBuilder.of(resolveMaterial(item.getMaterial(), Material.STONE))
                .name(item.getName())
                .lore(item.getLore())
                .build();
    }

    public boolean canShowQuestResetButton() {
        Config.QuestResetButton button = config().getMenu().getResetButton();
        return button != null && button.isEnabled() && resetPurchaseService.hasAvailablePaymentMethods();
    }

    public boolean canShowQuestResetPayment(QuestResetPaymentType type) {
        return resetPurchaseService.isAvailable(type);
    }

    public Config.MenuButton questResetPaymentButton(QuestResetPaymentType type) {
        return resetPurchaseService.button(type);
    }

    public ItemStack buildQuestResetButtonItem(Player viewer, PlayerQuestState state) {
        QuestResetEligibility eligibility = questService.resetEligibility(viewer, state);
        Config.QuestResetButton button = config().getMenu().getResetButton();
        Config.MenuItem item = button == null || button.getItem() == null
                ? new Config.MenuItem("AMETHYST_SHARD", "<gold>Reset Quests", List.of("<gray><completed>/<required> completed."))
                : button.getItem();
        return buildTokenItem(item, resetTokens(viewer, eligibility, null), Material.AMETHYST_SHARD);
    }

    public ItemStack buildQuestResetPurchaseItem(Player viewer, Config.MenuItem item, QuestResetPaymentType type, QuestResetEligibility eligibility, Material fallback) {
        Config.MenuItem template = item == null
                ? new Config.MenuItem(fallback.name(), "<gold><payment>", List.of("<gray>Cost: <white><amount></white>"))
                : item;
        return buildTokenItem(template, resetTokens(viewer, eligibility, type), fallback);
    }

    public List<QuestDifficulty> difficulties() {
        return definitionService.difficulties();
    }

    private String difficultyRequirementsText(QuestDifficulty difficulty, PlayerQuestState state) {
        if (difficulty == null) {
            return "";
        }
        Map<String, Integer> unmet = difficulty.unmetRequirements(state);
        if (unmet.isEmpty()) {
            return "";
        }

        List<String> requirements = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : unmet.entrySet()) {
            String count = QuestNumberFormatter.format(entry.getValue());
            String noun = entry.getValue() == 1 ? "quest" : "quests";
            requirements.add(count + " " + entry.getKey() + " " + noun);
        }
        return String.join(", ", requirements);
    }

    public List<QuestStreakMilestone> streakMilestones() {
        return streakService.milestones();
    }

    public ItemStack buildQuestItem(Player viewer, GeneratedQuest quest) {
        Config.QuestItem template = quest.completed()
                ? config().getMenu().getCompletedQuest()
                : config().getMenu().getActiveQuest();
        ItemStack item = new ItemStack(resolveMaterial(template.getMaterial(), quest.completed() ? Material.WRITABLE_BOOK : Material.BOOK));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = premiumDisplayName(replaceMenuTokens(template.getName(), quest, viewer), quest);
        meta.displayName(withoutItalics(parse(displayName)));
        List<Component> lore = new ArrayList<>();
        QuestDifficulty difficulty = definitionService.difficulty(quest.difficultyId());
        for (String line : template.getLore()) {
            if (addDescriptionLore(lore, line, quest.description(), descriptionLine ->
                    questService.replaceRawVariableDisplayValues(replaceMenuTokens(descriptionLine, quest, viewer), quest))) {
                continue;
            }
            if ("<difficulty_lore>".equalsIgnoreCase(line.trim())) {
                addDifficultyLore(lore, difficulty, difficultyLine -> replaceMenuTokens(difficultyLine, quest, viewer));
                continue;
            }
            if ("<premium_lore>".equalsIgnoreCase(line.trim())) {
                addPremiumLore(lore, quest, premiumLine -> replaceMenuTokens(premiumLine, quest, viewer));
                continue;
            }
            lore.add(withoutItalics(parse(replaceMenuTokens(line, quest, viewer))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        applyPremiumTooltipStyle(item, quest);
        return item;
    }

    public ItemStack buildGlobalQuestItem(Player viewer, GlobalQuestState state) {
        if (state == null) {
            return buildMenuItem(config().getGlobalQuests().getMenu().getNoActiveQuest());
        }

        Config.QuestItem template = state.quest().completed()
                ? config().getGlobalQuests().getMenu().getCompletedQuest()
                : config().getGlobalQuests().getMenu().getActiveQuest();
        ItemStack item = new ItemStack(resolveMaterial(template.getMaterial(), state.quest().completed() ? Material.EMERALD : Material.NETHER_STAR));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(withoutItalics(parse(globalQuestService.replaceGlobalTokens(template.getName(), state, viewer))));
        List<Component> lore = new ArrayList<>();
        for (String line : template.getLore()) {
            if (addDescriptionLore(lore, line, state.quest().description(), descriptionLine -> globalQuestService.replaceGlobalTokens(descriptionLine, state, viewer))) {
                continue;
            }
            lore.add(withoutItalics(parse(globalQuestService.replaceGlobalTokens(line, state, viewer))));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public List<Config.GlobalRewardTierConfig> fullGlobalRewardTiers() {
        return globalQuestService.fullRewardTiers();
    }

    public List<Config.GlobalRewardTierConfig> reducedGlobalRewardTiers() {
        return globalQuestService.reducedRewardTiers();
    }

    public ItemStack buildGlobalRewardPreviewItem(Config.GlobalRewardTierConfig tier, boolean reduced) {
        Config.GlobalRewardPreviewMenu menu = config().getGlobalQuests().getMenu().getRewardPreview();
        Config.MenuItem defaultTemplate = reduced ? menu.getReducedRewardItem() : menu.getFullRewardItem();
        Config.MenuItem template = tier.getItem() == null ? defaultTemplate : tier.getItem();
        Material fallback = reduced ? Material.GOLD_INGOT : Material.EMERALD;
        MapTokens tokens = new MapTokens()
                .putNumber("reward_percentile", tier.getPercentile())
                .put("reward_display_name", tier.getDisplayName());
        return buildTokenItem(template, tokens, fallback);
    }

    public ItemStack buildEmptyQuestSlotItem(int slotIndex) {
        MapTokens tokens = new MapTokens()
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex);
        return buildTokenItem(config().getMenu().getEmptyQuest(), tokens, Material.GRAY_DYE);
    }

    public ItemStack buildLockedPremiumQuestSlotItem(int slotIndex) {
        int premiumSlotIndex = slotIndex - questService.dailyQuestSlots();
        MapTokens tokens = new MapTokens()
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex)
                .putNumber("premium_slot", premiumSlotIndex + 1)
                .putNumber("premium_slot_index", premiumSlotIndex)
                .put("rank", lockedPremiumRank(slotIndex));
        Config.PremiumQuestMenu menu = premiumMenu();
        Config.MenuItem lockedQuest = menu.getLockedQuest() == null
                ? new Config.MenuItem("BARRIER", "<gold>Premium Quest <slot>", List.of("<gray>Requires <rank></gray>"))
                : menu.getLockedQuest();
        return buildTokenItem(lockedQuest, tokens, Material.BARRIER);
    }

    public String lockedPremiumRank(int slotIndex) {
        Config.PremiumQuestMenu menu = premiumMenu();
        Map<String, String> ranks = menu.getLockedRanks() == null ? Map.of() : menu.getLockedRanks();
        String displayedSlot = Integer.toString(slotIndex + 1);
        String rank = ranks.get(displayedSlot);
        return rank == null ? "" : rank;
    }

    public ItemStack buildDifficultyPickerItem(QuestDifficulty difficulty, int slotIndex) {
        MapTokens tokens = new MapTokens()
                .put("quest_difficulty", difficulty.displayName())
                .put("difficulty", difficulty.displayName())
                .put("difficulty_id", difficulty.id())
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex);
        return buildTokenItem(config().getMenu().getDifficultyPicker().getDifficultyItem(), tokens, Material.BOOK, difficulty.lore(), "difficulty_lore");
    }

    public ItemStack buildDifficultyItem(PlayerQuestState state, QuestDifficulty difficulty) {
        MapTokens tokens = new MapTokens()
                .put("quest_difficulty", difficulty.displayName())
                .put("difficulty", difficulty.displayName())
                .put("difficulty_id", difficulty.id())
                .putNumber("completed", state.difficultyCompletions(difficulty.id()))
                .putNumber("milestone_count", difficulty.milestones().size());
        return buildTokenItem(config().getMilestoneMenu().getDifficultyItem(), tokens, Material.BOOK);
    }

    public ItemStack buildMilestoneItem(PlayerQuestState state, QuestDifficulty difficulty, QuestMilestone milestone) {
        int completed = state.difficultyCompletions(difficulty.id());
        boolean reached = completed >= milestone.completed();
        boolean claimed = state.hasExecutedMilestone(difficulty.id(), milestone.completed());
        Config.MilestoneItem template = reached
                ? claimed ? config().getMilestoneMenu().getClaimedMilestone() : config().getMilestoneMenu().getUnlockedMilestone()
                : config().getMilestoneMenu().getLockedMilestone();
        MapTokens tokens = new MapTokens()
                .put("quest_difficulty", difficulty.displayName())
                .put("difficulty", difficulty.displayName())
                .put("difficulty_id", difficulty.id())
                .putNumber("completed", completed)
                .putNumber("milestone_completed", milestone.completed())
                .put("milestone_display_name", milestone.displayName());
        return buildTokenItem(template, tokens, reached ? Material.EMERALD : Material.GRAY_DYE, milestone.lore());
    }

    public ItemStack buildStreakStatusItem(Player viewer, PlayerQuestState questState, QuestStreakState streakState) {
        return buildTokenItem(config().getStreaks().getMenu().getStatusItem(), streakTokens(viewer, questState, streakState).putNumber("milestone_streak", 0).put("milestone_display_name", ""), Material.CLOCK);
    }

    public ItemStack buildStreakShieldButton(Player viewer, PlayerQuestState questState, QuestStreakState streakState) {
        return buildTokenItem(config().getStreaks().getMenu().getShieldButton(), streakTokens(viewer, questState, streakState).putNumber("milestone_streak", 0).put("milestone_display_name", ""), Material.SHIELD);
    }

    public ItemStack buildStreakRecoveryButton(Player viewer, PlayerQuestState questState, QuestStreakState streakState) {
        return buildTokenItem(config().getStreaks().getMenu().getRecoveryButton(), streakTokens(viewer, questState, streakState).putNumber("milestone_streak", 0).put("milestone_display_name", ""), Material.TOTEM_OF_UNDYING);
    }

    public ItemStack buildStreakMilestoneItem(Player viewer, PlayerQuestState questState, QuestStreakState streakState, QuestStreakMilestone milestone) {
        boolean reached = streakState.currentStreak() >= milestone.streak();
        Config.StreakMilestoneItem template = reached
                ? config().getStreaks().getMenu().getClaimedMilestone()
                : config().getStreaks().getMenu().getLockedMilestone();
        MapTokens tokens = streakTokens(viewer, questState, streakState)
                .putNumber("milestone_streak", milestone.streak())
                .put("milestone_display_name", milestone.displayName());
        tokens.put("streak_locked", tokens.apply(config().getMessages().getStreakMilestoneLocked()))
                .put("streak_completed", tokens.apply(config().getMessages().getStreakMilestoneCompleted()))
                .put("streak_progress", tokens.apply(config().getMessages().getStreakMilestoneProgress()));
        return buildTokenItem(template, tokens, reached ? Material.EMERALD : Material.GRAY_DYE, milestone.lore());
    }

    public MapTokens streakTokens(PlayerQuestState questState, QuestStreakState streakState) {
        return streakTokens(null, questState, streakState);
    }

    public MapTokens streakTokens(Player viewer, PlayerQuestState questState, QuestStreakState streakState) {
        List<GeneratedQuest> eligibleQuests = viewer == null ? questState.quests() : questService.accessibleQuests(viewer, questState);
        int required = streakService.dailyRequiredCompletions(questState, eligibleQuests);
        return new MapTokens()
                .putNumber("streak", streakState.currentStreak())
                .putNumber("highest_streak", streakState.highestStreak())
                .putNumber("daily_completed", streakService.dailyCompleted(eligibleQuests))
                .putNumber("daily_required", required)
                .putNumber("shield_balance", streakState.shieldBalance())
                .putNumber("recovery_balance", streakState.recoveryBalance())
                .putNumber("last_lost_streak", streakState.lastLostStreak())
                .putNumber("recovery_days_remaining", streakService.recoveryDaysRemaining(streakState, resetService.currentResetKey()));
    }

    private MapTokens resetTokens(Player viewer, QuestResetEligibility eligibility, QuestResetPaymentType paymentType) {
        String resetKey = resetService.currentResetKey();
        int used = viewer == null ? 0 : questService.cachedQuestResetPurchasesUsed(viewer.getUniqueId(), resetKey);
        int limit = questService.questResetDailyLimit();
        int remaining = Math.max(0, limit - used);
        return new MapTokens()
                .putNumber("completed", eligibility.completed())
                .putNumber("required", eligibility.required())
                .putNumber("resets_used", used)
                .putNumber("resets_limit", limit)
                .putNumber("resets_remaining", remaining)
                .put("status", questResetStatus(eligibility, remaining))
                .put("payment", paymentType == null ? "" : resetPurchaseService.displayPaymentName(paymentType))
                .put("reward", paymentType == null ? "" : resetPurchaseService.displayPaymentName(paymentType))
                .put("amount", paymentType == null ? "" : resetPurchaseService.displayAmount(paymentType));
    }

    private String questResetStatus(QuestResetEligibility eligibility, int remainingResets) {
        Config.QuestResetMenu menu = config().getMenu().getResetMenu();
        Config.QuestResetMenu resetMenu = menu == null ? new Config.QuestResetMenu() : menu;
        if (remainingResets <= 0) {
            return resetMenu.getStatusLimitReached();
        }
        return eligibility.eligible() ? resetMenu.getStatusReady() : resetMenu.getStatusIncomplete();
    }

    public void applyStreakRecovery(Player player) {
        streakService.recoverStreak(player, resetService.currentResetKey())
                .thenAccept(result -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (result.applied()) {
                        config().getMessages().getStreakRecoveryApplied().send(player, Placeholder.unparsed("streak", QuestNumberFormatter.format(result.evaluation().state().currentStreak())));
                    } else {
                        config().getMessages().getStreakRecoveryUnavailable().send(player);
                    }
                    openStreaks(player);
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to recover QuestsPlus streak for {}.", player.getUniqueId(), throwable);
                    return null;
                });
    }

    public void applyQuestResetPurchase(Player player, QuestResetPaymentType paymentType) {
        if (resetPurchaseService.isProcessing(player) || !resetPurchaseService.begin(player)) {
            config().getMessages().getQuestResetProcessing().send(player);
            return;
        }

        String resetKey = resetService.currentResetKey();
        questService.ensurePlayerStateAsync(player, resetKey)
                .thenCompose(state -> questService.refreshQuestResetPurchaseUsageAsync(player.getUniqueId(), resetKey).thenApply(unused -> state))
                .thenAccept(state -> Scheduler.entity(player).run(task -> {
                    if (!player.isOnline()) {
                        resetPurchaseService.finish(player);
                        return;
                    }
                    QuestResetEligibility eligibility = questService.resetEligibility(player, state);
                    if (!eligibility.eligible()) {
                        try {
                            config().getMessages().getQuestResetNotReady().send(
                                    player,
                                    Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                                    Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required()))
                            );
                            openDailyQuests(player);
                        } finally {
                            resetPurchaseService.finish(player);
                        }
                        return;
                    }
                    if (questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey) <= 0) {
                        try {
                            sendResetLimitReached(player, eligibility);
                            openDailyQuests(player);
                        } finally {
                            resetPurchaseService.finish(player);
                        }
                        return;
                    }
                    if (!resetPurchaseService.isAvailable(paymentType)) {
                        try {
                            sendPurchaseUnavailable(player, paymentType, eligibility);
                            openQuestResetPurchase(player);
                        } finally {
                            resetPurchaseService.finish(player);
                        }
                        return;
                    }
                    if (!resetPurchaseService.charge(player, paymentType)) {
                        try {
                            sendPurchaseUnavailable(player, paymentType, eligibility);
                            openQuestResetPurchase(player);
                        } finally {
                            resetPurchaseService.finish(player);
                        }
                        return;
                    }
                    questService.recordQuestResetPurchaseAndReset(player.getUniqueId(), resetKey)
                            .thenAccept(applied -> Scheduler.entity(player).run(doneTask -> {
                                try {
                                    if (player.isOnline()) {
                                        if (applied) {
                                            config().getMessages().getQuestResetSuccess().send(
                                                    player,
                                                    Placeholder.unparsed("payment", resetPurchaseService.displayPaymentName(paymentType)),
                                                    Placeholder.unparsed("reward", resetPurchaseService.displayPaymentName(paymentType)),
                                                    Placeholder.unparsed("amount", resetPurchaseService.displayAmount(paymentType)),
                                                    Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                                                    Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                                                    Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetKey))),
                                                    Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                                                    Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey)))
                                            );
                                        } else {
                                            sendResetLimitReached(player, eligibility);
                                        }
                                        openDailyQuests(player);
                                    }
                                } finally {
                                    resetPurchaseService.finish(player);
                                }
                            }))
                            .exceptionally(throwable -> {
                                log.error("Failed to reset QuestsPlus after purchase choice {} for {}.", paymentType, player.getUniqueId(), throwable);
                                Scheduler.entity(player).run(failedTask -> {
                                    try {
                                        if (player.isOnline()) {
                                            config().getMessages().getQuestResetFailed().send(player);
                                        }
                                    } finally {
                                        resetPurchaseService.finish(player);
                                    }
                                });
                                return null;
                            });
                }))
                .exceptionally(throwable -> {
                    log.error("Failed to load QuestsPlus state before reset purchase choice {} for {}.", paymentType, player.getUniqueId(), throwable);
                    Scheduler.entity(player).run(task -> {
                        try {
                            if (player.isOnline()) {
                                config().getMessages().getQuestResetFailed().send(player);
                            }
                        } finally {
                            resetPurchaseService.finish(player);
                        }
                    });
                    return null;
                });
    }

    private void sendPurchaseUnavailable(Player player, QuestResetPaymentType paymentType, QuestResetEligibility eligibility) {
        config().getMessages().getQuestResetPurchaseUnavailable().send(
                player,
                Placeholder.unparsed("payment", paymentType == null ? "Quest Reset currency" : resetPurchaseService.displayPaymentName(paymentType)),
                Placeholder.unparsed("reward", paymentType == null ? "Quest Reset currency" : resetPurchaseService.displayPaymentName(paymentType)),
                Placeholder.unparsed("amount", paymentType == null ? "" : resetPurchaseService.displayAmount(paymentType)),
                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetService.currentResetKey()))),
                Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetService.currentResetKey())))
        );
    }

    private void sendResetLimitReached(Player player, QuestResetEligibility eligibility) {
        String resetKey = resetService.currentResetKey();
        config().getMessages().getQuestResetLimitReached().send(
                player,
                Placeholder.unparsed("completed", QuestNumberFormatter.format(eligibility.completed())),
                Placeholder.unparsed("required", QuestNumberFormatter.format(eligibility.required())),
                Placeholder.unparsed("resets_used", QuestNumberFormatter.format(questService.cachedQuestResetPurchasesUsed(player.getUniqueId(), resetKey))),
                Placeholder.unparsed("resets_limit", QuestNumberFormatter.format(questService.questResetDailyLimit())),
                Placeholder.unparsed("resets_remaining", QuestNumberFormatter.format(questService.questResetPurchasesRemaining(player.getUniqueId(), resetKey)))
        );
    }

    public ItemStack buildTokenItem(Config.MenuItem item, MapTokens tokens, Material fallback) {
        return buildTokenItem(item, tokens, fallback, List.of());
    }

    public ItemStack buildTokenItem(Config.MenuItem item, MapTokens tokens, Material fallback, List<String> appendedLore) {
        return buildTokenItem(item, tokens, fallback, appendedLore, null);
    }

    public ItemStack buildTokenItem(Config.MenuItem item, MapTokens tokens, Material fallback, List<String> appendedLore, String lorePlaceholder) {
        ItemStack built = new ItemStack(resolveMaterial(item.getMaterial(), fallback));
        ItemMeta meta = built.getItemMeta();
        if (meta == null) {
            return built;
        }

        meta.displayName(withoutItalics(parse(tokens.apply(item.getName()))));
        List<Component> lore = new ArrayList<>();
        for (String line : item.getLore()) {
            if (lorePlaceholder != null && line != null && line.trim().equalsIgnoreCase("<" + lorePlaceholder + ">")) {
                for (String appendedLine : appendedLore == null ? List.<String>of() : appendedLore) {
                    lore.add(withoutItalics(parse(tokens.apply(appendedLine))));
                }
                continue;
            }
            lore.add(withoutItalics(parse(tokens.apply(line))));
        }
        if (lorePlaceholder == null) {
            for (String line : appendedLore == null ? List.<String>of() : appendedLore) {
                lore.add(withoutItalics(parse(tokens.apply(line))));
            }
        }
        meta.lore(lore);
        built.setItemMeta(meta);
        return built;
    }

    private void addDifficultyLore(List<Component> lore, QuestDifficulty difficulty, java.util.function.Function<String, String> tokens) {
        if (difficulty == null || difficulty.lore() == null || difficulty.lore().isEmpty()) {
            return;
        }
        for (String line : difficulty.lore()) {
            lore.add(withoutItalics(parse(tokens.apply(line))));
        }
    }

    private void addPremiumLore(List<Component> lore, GeneratedQuest quest, java.util.function.Function<String, String> tokens) {
        if (quest == null || !quest.premium()) {
            return;
        }
        Config.PremiumQuestMenu menu = premiumMenu();
        Map<String, List<String>> configuredLore = menu == null ? Map.of() : menu.getLore();
        if (configuredLore == null || configuredLore.isEmpty()) {
            return;
        }
        List<String> difficultyLore = configuredLore.get(QuestNames.normalize(quest.difficultyId()));
        if (difficultyLore == null || difficultyLore.isEmpty()) {
            return;
        }
        for (String line : difficultyLore) {
            lore.add(withoutItalics(parse(tokens.apply(line))));
        }
    }

    private boolean addDescriptionLore(List<Component> lore, String templateLine, List<String> description, java.util.function.Function<String, String> tokens) {
        if (templateLine == null || !templateLine.toLowerCase(java.util.Locale.ROOT).contains("<quest_description>")) {
            return false;
        }
        for (String descriptionLine : description == null ? List.<String>of() : description) {
            String expanded = QUEST_DESCRIPTION_TOKEN.matcher(templateLine)
                    .replaceAll(java.util.regex.Matcher.quoteReplacement(descriptionLine == null ? "" : descriptionLine));
            String rendered = tokens.apply(expanded);
            lore.add(withoutItalics(parse(rendered)));
        }
        return true;
    }

    private String replaceMenuTokens(String template, GeneratedQuest quest, Player viewer) {
        String resetKey = resetService.currentResetKey();
        int rerollLimit = questService.rerollLimit(viewer);
        int rerollsUsed = questService.cachedRerollsUsed(viewer.getUniqueId(), resetKey);
        return questService.replaceQuestTokens(template, quest, viewer)
                .replace("<quest_reset_timer>", resetService.resetTimerPlaceholder())
                .replace("<rerolls_used>", QuestNumberFormatter.format(rerollsUsed))
                .replace("<rerolls_limit>", QuestNumberFormatter.format(rerollLimit))
                .replace("<rerolls_remaining>", QuestNumberFormatter.format(Math.max(0, rerollLimit - rerollsUsed)));
    }

    private void applyPremiumTooltipStyle(ItemStack item, GeneratedQuest quest) {
        if (!quest.premium()) {
            return;
        }
        Config.PremiumQuestMenu menu = premiumMenu();
        String tooltipStyle = menu == null ? "" : menu.getTooltipStyle();
        if (tooltipStyle == null || tooltipStyle.isBlank()) {
            return;
        }
        try {
            item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key(tooltipStyle));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus premium tooltip style '{}'.", tooltipStyle);
        }
    }

    private String premiumDisplayName(String displayName, GeneratedQuest quest) {
        if (!quest.premium()) {
            return displayName;
        }
        Config.PremiumQuestMenu menu = premiumMenu();
        String suffix = menu == null ? "" : menu.getDisplayNameSuffix();
        if (suffix == null || suffix.isBlank()) {
            return displayName;
        }
        return displayName + suffix;
    }

    private Config.PremiumQuestMenu premiumMenu() {
        Config.PremiumQuestsFile premium = config().getPremiumQuests();
        Config.PremiumQuestMenu menu = premium == null ? null : premium.getMenu();
        return menu == null ? new Config.PremiumQuestMenu() : menu;
    }

    private String difficultyDisplayName(String difficultyId) {
        QuestDifficulty difficulty = definitionService.difficulty(difficultyId);
        return difficulty == null ? difficultyId : difficulty.displayName();
    }

    private Component parse(String input) {
        return miniMessage.deserialize(input == null ? "" : input);
    }

    private Component withoutItalics(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private Material resolveMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    public static class MapTokens {
        private final java.util.Map<String, String> values = new java.util.LinkedHashMap<>();

        public MapTokens put(String key, String value) {
            values.put(key, value == null ? "" : value);
            return this;
        }

        public MapTokens putNumber(String key, long value) {
            values.put(key, QuestNumberFormatter.format(value));
            return this;
        }

        public String apply(String template) {
            String output = template == null ? "" : template;
            for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
                output = output.replace("<" + entry.getKey() + ">", entry.getValue());
            }
            return output;
        }
    }

    private record StreakMenuState(PlayerQuestState questState, QuestStreakState streakState) {
    }

    private record DailyMenuState(PlayerQuestState questState, GlobalQuestState globalState) {
    }

}
