package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.domain.model.PlayerQuestState;
import gg.moonrise.quests.domain.model.QuestResetEligibility;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Answers which quest slots exist and which a player may use: the menu slot layout, the premium slots unlocked by
 * permissions, the per-player reroll limit and whether a player finished enough slots to buy a quest reset.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestSlotService {

    private final ConfigProvider configProvider;

    private final AtomicReference<ConfigDerived> configDerivedCache = new AtomicReference<>();

    /**
     * Returns the quest slot layout of the active config.
     */
    public QuestMenuLayout menuLayout() {
        return configDerived().layout();
    }

    public int dailyQuestSlots() {
        return menuLayout().dailyQuestSlots();
    }

    public int totalQuestSlots(Player player) {
        ConfigDerived derived = configDerived();
        QuestMenuLayout layout = derived.layout();
        return Math.min(layout.capacity(), layout.dailyQuestSlots() + premiumSlotLimit(player, derived));
    }

    public int totalVisibleQuestSlots() {
        return menuLayout().totalVisibleQuestSlots();
    }

    public int visiblePremiumSlotLimit() {
        return menuLayout().visiblePremiumSlots();
    }

    public int premiumSlotLimit(Player player) {
        return premiumSlotLimit(player, configDerived());
    }

    public boolean isPremiumSlot(int slotIndex) {
        return menuLayout().isPremiumSlot(slotIndex);
    }

    public boolean canAccessSlot(Player player, int slotIndex) {
        ConfigDerived derived = configDerived();
        QuestMenuLayout layout = derived.layout();
        if (slotIndex < 0 || slotIndex >= layout.totalVisibleQuestSlots()) {
            return false;
        }
        if (!layout.isPremiumSlot(slotIndex)) {
            return true;
        }
        return slotIndex < layout.dailyQuestSlots() + premiumSlotLimit(player, derived);
    }

    public boolean canAccessQuest(Player player, GeneratedQuest quest) {
        if (!quest.premium()) {
            return true;
        }
        return canAccessSlot(player, quest.slotIndex());
    }

    public List<GeneratedQuest> accessibleQuests(Player player, PlayerQuestState state) {
        return state.quests().stream()
                .filter(quest -> canAccessQuest(player, quest))
                .toList();
    }

    public QuestResetEligibility resetEligibility(Player player, PlayerQuestState state) {
        int required = totalQuestSlots(player);
        int completed = 0;
        for (int slotIndex = 0; slotIndex < required; slotIndex++) {
            boolean completedQuest = state.questAtSlot(slotIndex)
                    .filter(quest -> quest.completed() && canAccessQuest(player, quest))
                    .isPresent();
            if (completedQuest) {
                completed++;
            }
        }
        return new QuestResetEligibility(completed, required);
    }

    public int rerollLimit(Player player) {
        return configDerived().rerollLimits().limitFor(player);
    }

    private int premiumSlotLimit(Player player, ConfigDerived derived) {
        QuestMenuLayout layout = derived.layout();
        return Math.clamp(derived.premiumLimits().limitFor(player), 0, Math.max(0, layout.capacity() - layout.dailyQuestSlots()));
    }

    /**
     * Returns the values derived from the active config. They are computed once per config instance; a reload
     * swaps the instance in {@link ConfigProvider}, which makes the next call compute them again.
     */
    private ConfigDerived configDerived() {
        Config config = configProvider.get();
        ConfigDerived cached = configDerivedCache.get();
        if (cached != null && cached.config() == config) {
            return cached;
        }
        ConfigDerived derived = new ConfigDerived(config, QuestMenuLayout.of(config), PermissionLimits.premiumSlots(config), PermissionLimits.rerolls(config));
        configDerivedCache.set(derived);
        return derived;
    }

    /**
     * Values computed from one config instance, which is compared by identity, not equality.
     */
    private record ConfigDerived(Config config, QuestMenuLayout layout, PermissionLimits premiumLimits, PermissionLimits rerollLimits) {
    }
}
