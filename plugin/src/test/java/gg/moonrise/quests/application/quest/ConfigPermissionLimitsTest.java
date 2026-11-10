package gg.moonrise.quests.application.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import gg.moonrise.quests.config.file.PremiumQuestsFile;
import gg.moonrise.quests.config.feature.Rerolls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConfigPermissionLimitsTest {

    @Test
    public void rerollAndPremiumDefaultsUseTheSameRankLimits() {
        Map<String, Integer> rerollLimits = new Rerolls().getPermissionLimits();
        Map<String, Integer> premiumLimits = new PremiumQuestsFile().getPermissionLimits();

        assertEquals(Map.of("vip", 1, "vipplus", 2, "mvp", 3), rerollLimits);
        assertEquals(List.of("vip", "vipplus", "mvp"), List.copyOf(rerollLimits.keySet()));
        assertEquals(rerollLimits, premiumLimits);
    }

    @Test
    public void eachConfigInstanceOwnsItsDefaultLimitMap() {
        Map<String, Integer> first = new Rerolls().getPermissionLimits();
        Map<String, Integer> second = new Rerolls().getPermissionLimits();
        Map<String, Integer> premium = new PremiumQuestsFile().getPermissionLimits();

        assertNotSame(first, second);
        assertNotSame(first, premium);

        first.put("vip", 99);

        assertEquals(1, second.get("vip"));
        assertEquals(1, premium.get("vip"));
    }
}
