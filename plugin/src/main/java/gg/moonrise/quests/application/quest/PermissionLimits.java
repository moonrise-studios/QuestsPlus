package gg.moonrise.quests.application.quest;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.file.PremiumQuestsFile;
import gg.moonrise.quests.config.feature.Rerolls;
import gg.moonrise.quests.util.QuestNames;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Permission-based limits such as {@code questsplus.reroll.<key>: <limit>}, parsed once from the config. A player's
 * limit is the highest limit among the permissions they have, or 0.
 *
 * @param highestConfigured the highest configured limit, whether or not anyone can hold its permission
 * @param tiers             the grantable limits, highest first
 */
public record PermissionLimits(int highestConfigured, @NotNull List<Tier> tiers) {

    private static final String PREMIUM_PERMISSION_PREFIX = "questsplus.premium.";
    private static final String REROLL_PERMISSION_PREFIX = "questsplus.reroll.";
    private static final PermissionLimits NONE = new PermissionLimits(0, List.of());

    public PermissionLimits {
        tiers = List.copyOf(tiers);
    }

    public static PermissionLimits none() {
        return NONE;
    }

    /**
     * Premium quest slot limits, or none if premium quests are disabled.
     */
    public static PermissionLimits premiumSlots(@NotNull Config config) {
        PremiumQuestsFile premium = config.getPremiumQuests();
        if (premium == null || !premium.isEnabled()) {
            return NONE;
        }
        return of(PREMIUM_PERMISSION_PREFIX, premium.getPermissionLimits());
    }

    /**
     * Daily reroll limits.
     */
    public static PermissionLimits rerolls(@NotNull Config config) {
        Rerolls rerolls = config.getDaily().getRerolls();
        return rerolls == null ? NONE : of(REROLL_PERMISSION_PREFIX, rerolls.getPermissionLimits());
    }

    /**
     * Parses {@code configured} keys into {@code permissionPrefix + normalized key}. Missing and negative limits
     * count as 0; entries with a blank key or a limit of 0 can never be granted.
     */
    public static PermissionLimits of(@NotNull String permissionPrefix, @Nullable Map<String, Integer> configured) {
        if (configured == null || configured.isEmpty()) {
            return NONE;
        }
        int highest = 0;
        List<Tier> tiers = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : configured.entrySet()) {
            int limit = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            highest = Math.max(highest, limit);
            String key = QuestNames.normalize(entry.getKey());
            if (!key.isBlank() && limit > 0) {
                tiers.add(new Tier(permissionPrefix + key, limit));
            }
        }
        tiers.sort(Comparator.comparingInt(Tier::limit).reversed());
        return new PermissionLimits(highest, tiers);
    }

    /**
     * Returns the highest limit whose permission {@code permissible} has, or 0.
     */
    public int limitFor(@NotNull Permissible permissible) {
        for (Tier tier : tiers) {
            if (permissible.hasPermission(tier.permission())) {
                return tier.limit();
            }
        }
        return 0;
    }

    public record Tier(@NotNull String permission, int limit) {
    }
}
