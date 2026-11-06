package gg.moonrise.quests.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConfigPermissionLimitsTest {

    @Test
    public void rerollAndPremiumDefaultsUseTheSameRankLimits() {
        Map<String, Integer> rerollLimits = new Config.Rerolls().getPermissionLimits();
        Map<String, Integer> premiumLimits = new Config.PremiumQuestsFile().getPermissionLimits();

        assertEquals(Map.of("vip", 1, "vipplus", 2, "mvp", 3), rerollLimits);
        assertEquals(List.of("vip", "vipplus", "mvp"), List.copyOf(rerollLimits.keySet()));
        assertEquals(rerollLimits, premiumLimits);
    }

    @Test
    public void eachConfigInstanceOwnsItsDefaultLimitMap() {
        Map<String, Integer> first = new Config.Rerolls().getPermissionLimits();
        Map<String, Integer> second = new Config.Rerolls().getPermissionLimits();
        Map<String, Integer> premium = new Config.PremiumQuestsFile().getPermissionLimits();

        assertNotSame(first, second);
        assertNotSame(first, premium);

        first.put("vip", 99);

        assertEquals(1, second.get("vip"));
        assertEquals(1, premium.get("vip"));
    }
}
