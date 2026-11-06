package gg.moonrise.quests.core.service;

import gg.moonrise.engine.paper.item.ItemBuilder;
import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.GlobalRewardPreviewMenu;
import gg.moonrise.quests.config.GlobalRewardTierConfig;
import gg.moonrise.quests.config.MenuItem;
import gg.moonrise.quests.config.MilestoneButton;
import gg.moonrise.quests.config.MilestoneItem;
import gg.moonrise.quests.config.PremiumQuestMenu;
import gg.moonrise.quests.config.PremiumQuestsFile;
import gg.moonrise.quests.config.QuestItem;
import gg.moonrise.quests.config.QuestResetButton;
import gg.moonrise.quests.config.QuestResetMenu;
import gg.moonrise.quests.config.StreakButton;
import gg.moonrise.quests.config.StreakMilestoneItem;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.model.PlayerQuestState;
import gg.moonrise.quests.model.QuestDifficulty;
import gg.moonrise.quests.model.QuestMilestone;
import gg.moonrise.quests.model.QuestResetEligibility;
import gg.moonrise.quests.model.QuestStreakMilestone;
import gg.moonrise.quests.model.QuestStreakState;
import gg.moonrise.quests.sdk.currency.QuestCurrency;
import gg.moonrise.quests.sdk.currency.QuestCurrencyButton;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.util.QuestNames;
import gg.moonrise.quests.util.QuestNumberFormatter;
import io.papermc.paper.datacomponent.DataComponentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the items shown in the QuestsPlus menus from the menu config, and answers which optional buttons the menus
 * show.
 */
@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class QuestMenuItems {

    private static final Pattern QUEST_DESCRIPTION_TOKEN = Pattern.compile("<quest_description>", Pattern.CASE_INSENSITIVE);

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestSlotService slotService;
    private final QuestUsageService usageService;
    private final QuestTextService textService;
    private final QuestResetService resetService;
    private final QuestStreakService streakService;
    private final GlobalQuestService globalQuestService;
    private final QuestResetCurrencies currencies;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Config config() {
        return configProvider.get();
    }

    public QuestMenuLayout menuLayout() {
        return slotService.menuLayout();
    }

    public boolean canAccessSlot(Player player, int slotIndex) {
        return slotService.canAccessSlot(player, slotIndex);
    }

    public ItemStack buildMenuItem(MenuItem item) {
        if (item == null) {
            return new ItemStack(Material.STONE);
        }
        return ItemBuilder.of(resolveMaterial(item.getMaterial(), Material.STONE))
                .name(item.getName())
                .lore(item.getLore())
                .build();
    }

    public boolean canShowQuestResetButton() {
        QuestResetButton button = config().getMenu().getResetButton();
        return button != null && button.isEnabled() && currencies.hasAvailableQuestResetCurrency();
    }

    public ItemStack buildQuestResetButtonItem(Player viewer, PlayerQuestState state) {
        QuestResetEligibility eligibility = slotService.resetEligibility(viewer, state);
        QuestResetButton button = config().getMenu().getResetButton();
        MenuItem item = button == null || button.getItem() == null
                ? new MenuItem("AMETHYST_SHARD", "<gold>Reset Quests", List.of("<gray><completed>/<required> completed."))
                : button.getItem();
        return buildTokenItem(item, resetTokens(viewer, eligibility, null), Material.AMETHYST_SHARD);
    }

    public ItemStack buildQuestResetPurchaseItem(Player viewer, QuestCurrencyButton button, QuestCurrency currency, QuestResetEligibility eligibility) {
        MenuItem template = button == null
                ? new MenuItem("PAPER", "<gold><payment>", List.of("<gray>Cost: <white><amount></white>"))
                : new MenuItem(button.material(), button.name(), button.lore());
        return buildTokenItem(template, resetTokens(viewer, eligibility, currency), Material.PAPER);
    }

    public List<QuestDifficulty> difficulties() {
        return definitionService.difficulties();
    }

    public List<QuestStreakMilestone> streakMilestones() {
        return streakService.milestones();
    }

    public boolean canShowMilestoneButton() {
        MilestoneButton button = config().getMenu().getMilestoneButton();
        return button != null && button.isEnabled() && config().isQuestMilestonesEnabled();
    }

    public boolean canShowStreakButton() {
        StreakButton button = config().getMenu().getStreakButton();
        return button != null && button.isEnabled() && streakService.isEnabled();
    }

    public ItemStack buildQuestItem(Player viewer, GeneratedQuest quest) {
        QuestItem template = questTemplate(quest.completed()
                ? config().getMenu().getCompletedQuest()
                : config().getMenu().getActiveQuest());
        ItemStack item = new ItemStack(resolveMaterial(template.getMaterial(), quest.completed() ? Material.WRITABLE_BOOK : Material.BOOK));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = premiumDisplayName(replaceMenuTokens(template.getName(), quest, viewer), quest);
        meta.displayName(withoutItalics(parse(displayName)));
        List<Component> lore = new ArrayList<>();
        List<String> difficultyLore = definitionService.difficulty(quest.difficultyId())
                .map(QuestDifficulty::lore)
                .orElse(List.of());
        for (String rawLine : template.getLore()) {
            String line = rawLine == null ? "" : rawLine;
            if (addDescriptionLore(lore, line, quest.description(), descriptionLine ->
                    textService.replaceRawVariableDisplayValues(replaceMenuTokens(descriptionLine, quest, viewer), quest))) {
                continue;
            }
            if ("<difficulty_lore>".equalsIgnoreCase(line.trim())) {
                addDifficultyLore(lore, difficultyLore, difficultyLine -> replaceMenuTokens(difficultyLine, quest, viewer));
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

        QuestItem template = questTemplate(state.quest().completed()
                ? config().getGlobalQuests().getMenu().getCompletedQuest()
                : config().getGlobalQuests().getMenu().getActiveQuest());
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

    private QuestItem questTemplate(QuestItem template) {
        return template == null ? new QuestItem() : template;
    }

    public List<GlobalRewardTierConfig> fullGlobalRewardTiers() {
        return globalQuestService.fullRewardTiers();
    }

    public List<GlobalRewardTierConfig> reducedGlobalRewardTiers() {
        return globalQuestService.reducedRewardTiers();
    }

    public ItemStack buildGlobalRewardPreviewItem(GlobalRewardTierConfig tier, boolean reduced) {
        GlobalRewardPreviewMenu menu = config().getGlobalQuests().getMenu().getRewardPreview();
        MenuItem defaultTemplate = reduced ? menu.getReducedRewardItem() : menu.getFullRewardItem();
        MenuItem template = tier.getItem() == null ? defaultTemplate : tier.getItem();
        Material fallback = reduced ? Material.GOLD_INGOT : Material.EMERALD;
        MenuTokens tokens = new MenuTokens()
                .putNumber("reward_percentile", tier.getPercentile())
                .put("reward_display_name", tier.getDisplayName());
        return buildTokenItem(template, tokens, fallback);
    }

    public ItemStack buildEmptyQuestSlotItem(int slotIndex) {
        MenuTokens tokens = new MenuTokens()
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex);
        return buildTokenItem(config().getMenu().getEmptyQuest(), tokens, Material.GRAY_DYE);
    }

    public ItemStack buildLockedPremiumQuestSlotItem(int slotIndex) {
        int premiumSlotIndex = slotIndex - slotService.dailyQuestSlots();
        MenuTokens tokens = new MenuTokens()
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex)
                .putNumber("premium_slot", premiumSlotIndex + 1)
                .putNumber("premium_slot_index", premiumSlotIndex)
                .put("rank", lockedPremiumRank(slotIndex));
        PremiumQuestMenu menu = premiumMenu();
        MenuItem lockedQuest = menu.getLockedQuest() == null
                ? new MenuItem("BARRIER", "<gold>Premium Quest <slot>", List.of("<gray>Requires <rank></gray>"))
                : menu.getLockedQuest();
        return buildTokenItem(lockedQuest, tokens, Material.BARRIER);
    }

    public String lockedPremiumRank(int slotIndex) {
        PremiumQuestMenu menu = premiumMenu();
        Map<String, String> ranks = menu.getLockedRanks() == null ? Map.of() : menu.getLockedRanks();
        String displayedSlot = Integer.toString(slotIndex + 1);
        String rank = ranks.get(displayedSlot);
        return rank == null ? "" : rank;
    }

    public ItemStack buildDifficultyPickerItem(QuestDifficulty difficulty, int slotIndex) {
        MenuTokens tokens = new MenuTokens()
                .put("quest_difficulty", difficulty.displayName())
                .put("difficulty", difficulty.displayName())
                .put("difficulty_id", difficulty.id())
                .putNumber("slot", slotIndex + 1)
                .putNumber("slot_index", slotIndex);
        return buildTokenItem(config().getMenu().getDifficultyPicker().getDifficultyItem(), tokens, Material.BOOK, difficulty.lore(), "difficulty_lore");
    }

    public ItemStack buildDifficultyItem(PlayerQuestState state, QuestDifficulty difficulty) {
        MenuTokens tokens = new MenuTokens()
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
        MilestoneItem template = reached
                ? claimed ? config().getMilestoneMenu().getClaimedMilestone() : config().getMilestoneMenu().getUnlockedMilestone()
                : config().getMilestoneMenu().getLockedMilestone();
        MenuTokens tokens = new MenuTokens()
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
        StreakMilestoneItem template = reached
                ? config().getStreaks().getMenu().getClaimedMilestone()
                : config().getStreaks().getMenu().getLockedMilestone();
        MenuTokens tokens = streakTokens(viewer, questState, streakState)
                .putNumber("milestone_streak", milestone.streak())
                .put("milestone_display_name", milestone.displayName());
        tokens.put("streak_locked", tokens.apply(config().getMessages().getStreakMilestoneLocked()))
                .put("streak_completed", tokens.apply(config().getMessages().getStreakMilestoneCompleted()))
                .put("streak_progress", tokens.apply(config().getMessages().getStreakMilestoneProgress()));
        return buildTokenItem(template, tokens, reached ? Material.EMERALD : Material.GRAY_DYE, milestone.lore());
    }

    public MenuTokens streakTokens(Player viewer, PlayerQuestState questState, QuestStreakState streakState) {
        List<GeneratedQuest> eligibleQuests = viewer == null ? questState.quests() : slotService.accessibleQuests(viewer, questState);
        int required = streakService.dailyRequiredCompletions(questState, eligibleQuests);
        return new MenuTokens()
                .putNumber("streak", streakState.currentStreak())
                .putNumber("highest_streak", streakState.highestStreak())
                .putNumber("daily_completed", streakService.dailyCompleted(eligibleQuests))
                .putNumber("daily_required", required)
                .putNumber("shield_balance", streakState.shieldBalance())
                .putNumber("recovery_balance", streakState.recoveryBalance())
                .putNumber("last_lost_streak", streakState.lastLostStreak())
                .putNumber("recovery_days_remaining", streakService.recoveryDaysRemaining(streakState, resetService.currentResetKey()));
    }

    private MenuTokens resetTokens(Player viewer, QuestResetEligibility eligibility, QuestCurrency currency) {
        String resetKey = resetService.currentResetKey();
        int used = viewer == null ? 0 : usageService.cachedQuestResetPurchasesUsed(viewer.getUniqueId(), resetKey);
        int limit = usageService.questResetDailyLimit();
        int remaining = Math.max(0, limit - used);
        return new MenuTokens()
                .putNumber("completed", eligibility.completed())
                .putNumber("required", eligibility.required())
                .putNumber("resets_used", used)
                .putNumber("resets_limit", limit)
                .putNumber("resets_remaining", remaining)
                .put("status", questResetStatus(eligibility, remaining))
                .put("payment", currencies.displayName(currency, ""))
                .put("reward", currencies.displayName(currency, ""))
                .put("amount", currencies.displayAmount(currency, viewer));
    }

    private String questResetStatus(QuestResetEligibility eligibility, int remainingResets) {
        QuestResetMenu menu = config().getMenu().getResetMenu();
        QuestResetMenu resetMenu = menu == null ? new QuestResetMenu() : menu;
        if (remainingResets <= 0) {
            return resetMenu.getStatusLimitReached();
        }
        return eligibility.eligible() ? resetMenu.getStatusReady() : resetMenu.getStatusIncomplete();
    }

    public ItemStack buildTokenItem(MenuItem item, MenuTokens tokens, Material fallback) {
        return buildTokenItem(item, tokens, fallback, List.of());
    }

    public ItemStack buildTokenItem(MenuItem item, MenuTokens tokens, Material fallback, List<String> appendedLore) {
        return buildTokenItem(item, tokens, fallback, appendedLore, null);
    }

    public ItemStack buildTokenItem(MenuItem item, MenuTokens tokens, Material fallback, List<String> appendedLore, String lorePlaceholder) {
        if (item == null) {
            return new ItemStack(fallback);
        }
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

    private void addDifficultyLore(List<Component> lore, List<String> difficultyLore, Function<String, String> tokens) {
        for (String line : difficultyLore) {
            lore.add(withoutItalics(parse(tokens.apply(line))));
        }
    }

    private void addPremiumLore(List<Component> lore, GeneratedQuest quest, Function<String, String> tokens) {
        if (quest == null || !quest.premium()) {
            return;
        }
        PremiumQuestMenu menu = premiumMenu();
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

    private boolean addDescriptionLore(List<Component> lore, String templateLine, List<String> description, Function<String, String> tokens) {
        if (templateLine == null || !templateLine.toLowerCase(Locale.ROOT).contains("<quest_description>")) {
            return false;
        }
        for (String descriptionLine : description == null ? List.<String>of() : description) {
            String expanded = QUEST_DESCRIPTION_TOKEN.matcher(templateLine)
                    .replaceAll(Matcher.quoteReplacement(descriptionLine == null ? "" : descriptionLine));
            String rendered = tokens.apply(expanded);
            lore.add(withoutItalics(parse(rendered)));
        }
        return true;
    }

    private String replaceMenuTokens(String template, GeneratedQuest quest, Player viewer) {
        String resetKey = resetService.currentResetKey();
        int rerollLimit = slotService.rerollLimit(viewer);
        int rerollsUsed = usageService.cachedRerollsUsed(viewer.getUniqueId(), resetKey);
        return textService.replaceQuestTokens(template, quest, viewer)
                .replace("<quest_reset_timer>", resetService.resetTimerPlaceholder())
                .replace("<rerolls_used>", QuestNumberFormatter.format(rerollsUsed))
                .replace("<rerolls_limit>", QuestNumberFormatter.format(rerollLimit))
                .replace("<rerolls_remaining>", QuestNumberFormatter.format(Math.max(0, rerollLimit - rerollsUsed)));
    }

    private void applyPremiumTooltipStyle(ItemStack item, GeneratedQuest quest) {
        if (!quest.premium()) {
            return;
        }
        PremiumQuestMenu menu = premiumMenu();
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
        PremiumQuestMenu menu = premiumMenu();
        String suffix = menu == null ? "" : menu.getDisplayNameSuffix();
        if (suffix == null || suffix.isBlank()) {
            return displayName;
        }
        return displayName + suffix;
    }

    private PremiumQuestMenu premiumMenu() {
        PremiumQuestsFile premium = config().getPremiumQuests();
        PremiumQuestMenu menu = premium == null ? null : premium.getMenu();
        return menu == null ? new PremiumQuestMenu() : menu;
    }

    private String difficultyDisplayName(String difficultyId) {
        return definitionService.difficulty(difficultyId)
                .map(QuestDifficulty::displayName)
                .orElse(difficultyId);
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
    /**
     * The difficulty's display name parsed for chat, falling back to {@code difficultyId} when it is unknown.
     */
    public Component difficultyName(String difficultyId) {
        return withoutItalics(parse(difficultyDisplayName(difficultyId)));
    }
}
