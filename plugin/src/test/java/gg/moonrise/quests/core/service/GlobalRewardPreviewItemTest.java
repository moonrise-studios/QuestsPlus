package gg.moonrise.quests.core.service;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import gg.moonrise.quests.config.Config;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalRewardPreviewItemTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private QuestMenuService menuService;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        Config config = new Config();
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);

        menuService = new QuestMenuService(
                configProvider,
                mock(QuestDefinitionService.class),
                mock(QuestService.class),
                mock(QuestResetService.class),
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                mock(QuestResetPurchaseService.class)
        );
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void globalRewardPreviewUsesConfiguredTierItem() {
        Config.MenuItem item = new Config.MenuItem(
                "DIAMOND",
                "<green><reward_display_name>",
                List.of(
                        "<gray>Contributor tier: <white>Top <reward_percentile>%</white>",
                        "<yellow>Custom reward lore"
                )
        );
        Config.GlobalRewardTierConfig tier = new Config.GlobalRewardTierConfig(
                3,
                "<aqua>Custom Tier",
                item,
                List.of("eco give <player> 100")
        );

        ItemStack stack = menuService.buildGlobalRewardPreviewItem(tier, false);

        assertEquals(Material.DIAMOND, stack.getType());
        ItemMeta meta = assertMeta(stack);
        assertEquals("Custom Tier", PLAIN_TEXT.serialize(meta.displayName()));
        assertEquals(List.of("Contributor tier: Top 3%", "Custom reward lore"), plainLore(meta));
    }

    @Test
    void globalRewardPreviewFallsBackToSharedTemplateWhenTierItemIsUnset() {
        Config.GlobalRewardTierConfig tier = new Config.GlobalRewardTierConfig(
                10,
                "<yellow>Fallback Tier",
                List.of("eco give <player> 50")
        );

        ItemStack stack = menuService.buildGlobalRewardPreviewItem(tier, true);

        assertEquals(Material.GOLD_INGOT, stack.getType());
        ItemMeta meta = assertMeta(stack);
        assertEquals("Reduced Reward: Fallback Tier", PLAIN_TEXT.serialize(meta.displayName()));
        assertEquals(List.of("Quest completion: 50% - 99.9%", "Contributor tier: Top 10%"), plainLore(meta));
    }

    private static ItemMeta assertMeta(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.displayName());
        assertNotNull(meta.lore());
        return meta;
    }

    private static List<String> plainLore(ItemMeta meta) {
        return meta.lore().stream()
                .map(PLAIN_TEXT::serialize)
                .toList();
    }
}
