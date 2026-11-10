package gg.moonrise.quests.core.service.quest;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.file.DailyFile;
import gg.moonrise.quests.config.menu.PremiumQuestMenu;
import gg.moonrise.quests.config.file.PremiumQuestsFile;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionLimitsTest {

    @Test
    public void playerGetsTheHighestLimitAmongTheirPermissions() {
        PermissionLimits limits = PermissionLimits.of("questsplus.reroll.", limits("vip", 1, "Mvp_Plus", 3, "mvp", 2));
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.reroll.vip")).thenReturn(true);
        when(player.hasPermission("questsplus.reroll.mvp")).thenReturn(true);

        assertEquals(2, limits.limitFor(player));
        assertEquals(3, limits.highestConfigured());
        assertEquals("questsplus.reroll.mvp-plus", limits.tiers().getFirst().permission());
    }

    @Test
    public void playerWithoutPermissionsGetsZero() {
        PermissionLimits limits = PermissionLimits.of("questsplus.reroll.", limits("vip", 1, "mvp", 2, "elite", 4));

        assertEquals(0, limits.limitFor(mock(Player.class)));
    }

    @Test
    public void blankKeysAndNonPositiveLimitsAreNeverGranted() {
        Map<String, Integer> configured = new LinkedHashMap<>();
        configured.put(" ", 5);
        configured.put("negative", -2);
        configured.put("missing", null);
        configured.put("vip", 1);
        PermissionLimits limits = PermissionLimits.of("questsplus.premium.", configured);

        assertEquals(List.of(new PermissionLimits.Tier("questsplus.premium.vip", 1)), limits.tiers());
        // The visible premium slot count still uses every configured limit, as before.
        assertEquals(5, limits.highestConfigured());
    }

    @Test
    public void missingOrDisabledConfigHasNoLimits() {
        assertSame(PermissionLimits.none(), PermissionLimits.of("questsplus.reroll.", null));
        assertSame(PermissionLimits.none(), PermissionLimits.of("questsplus.reroll.", Map.of()));

        PremiumQuestsFile premium = new PremiumQuestsFile(limits("vip", 1, "mvp", 2, "elite", 3), new PremiumQuestMenu(Map.of()));
        Config config = Config.compose(null, new DailyFile(3), null, null, null, null, premium);
        Player player = mock(Player.class);
        when(player.hasPermission("questsplus.premium.mvp")).thenReturn(true);

        assertEquals(2, PermissionLimits.premiumSlots(config).limitFor(player));
    }

    private static Map<String, Integer> limits(String first, int firstLimit, String second, int secondLimit, String third, int thirdLimit) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put(first, firstLimit);
        limits.put(second, secondLimit);
        limits.put(third, thirdLimit);
        return limits;
    }
}
