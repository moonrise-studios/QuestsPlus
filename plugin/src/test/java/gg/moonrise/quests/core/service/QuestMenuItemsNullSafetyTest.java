package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestTypes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestMenuItemsNullSafetyTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private Player player;
    private Config config;
    private QuestTextService textService;
    private QuestStreakService streakService;
    private QuestMenuItems menuItems;

    @BeforeEach
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        player = server.addPlayer("NullSafety");
        config = new Config();
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(config);
        textService = mock(QuestTextService.class);
        streakService = mock(QuestStreakService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        when(resetService.currentResetKey()).thenReturn("daily");
        when(resetService.resetTimerPlaceholder()).thenReturn("");
        when(textService.replaceQuestTokens(any(), any(), any())).thenAnswer(invocation -> Objects.requireNonNullElse(invocation.getArgument(0), ""));
        when(textService.replaceRawVariableDisplayValues(any(), any())).thenAnswer(invocation -> Objects.requireNonNullElse(invocation.getArgument(0), ""));

        menuItems = new QuestMenuItems(
                configProvider,
                mock(QuestDefinitionService.class),
                mock(QuestSlotService.class),
                mock(QuestUsageService.class),
                textService,
                resetService,
                streakService,
                mock(GlobalQuestService.class),
                new QuestResetCurrencies(configProvider, mock(QuestResetPurchaseService.class))
        );
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void nullMilestoneAndStreakButtonsAreTreatedAsDisabled() throws ReflectiveOperationException {
        when(streakService.isEnabled()).thenReturn(true);
        assertTrue(menuItems.canShowMilestoneButton());
        assertTrue(menuItems.canShowStreakButton());

        setField(config.getMenu(), "milestoneButton", null);
        setField(config.getMenu(), "streakButton", null);

        assertFalse(menuItems.canShowMilestoneButton());
        assertFalse(menuItems.canShowStreakButton());
    }

    @Test
    public void tokenAndMenuItemsWithNullLoreRenderWithoutLore() {
        Config.MenuItem item = new Config.MenuItem("PAPER", "<gold>Paper", null);

        ItemStack tokenItem = menuItems.buildTokenItem(item, new MenuTokens(), Material.STONE);
        ItemStack menuItem = menuItems.buildMenuItem(item);

        assertEquals(Material.PAPER, tokenItem.getType());
        assertEquals("Paper", PLAIN_TEXT.serialize(meta(tokenItem).displayName()));
        assertEquals(List.of(), plainLore(meta(tokenItem)));
        assertEquals(Material.PAPER, menuItem.getType());
        assertEquals(List.of(), item.getLore());
    }

    @Test
    public void missingItemTemplatesFallBackInsteadOfThrowing() {
        assertEquals(Material.ARROW, menuItems.buildTokenItem(null, new MenuTokens(), Material.ARROW).getType());
        assertEquals(Material.STONE, menuItems.buildMenuItem(null).getType());
    }

    @Test
    public void questItemWithNullLoreOrTemplateRendersWithoutLore() throws ReflectiveOperationException {
        GeneratedQuest quest = quest(player.getUniqueId());
        setField(config.getMenu().getActiveQuest(), "lore", null);

        ItemStack withNullLore = menuItems.buildQuestItem(player, quest);

        assertEquals(Material.BOOK, withNullLore.getType());
        assertEquals(List.of(), plainLore(meta(withNullLore)));

        setField(config.getMenu(), "activeQuest", null);

        ItemStack withNullTemplate = menuItems.buildQuestItem(player, quest);

        assertEquals(Material.BOOK, withNullTemplate.getType());
        assertEquals(List.of(), plainLore(meta(withNullTemplate)));
    }

    private static ItemMeta meta(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        assertNotNull(meta);
        return meta;
    }

    private static List<String> plainLore(ItemMeta meta) {
        return meta.lore() == null ? List.of() : meta.lore().stream()
                .map(PLAIN_TEXT::serialize)
                .toList();
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static GeneratedQuest quest(UUID playerId) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                playerId,
                "daily",
                "kill-mobs",
                QuestTypes.KILL_MOB,
                "easy",
                "<green>Easy",
                "<green>Mob Hunt",
                List.of("<gray>Kill mobs."),
                Map.of("mob", "zombie"),
                0,
                10,
                0,
                false
        );
    }
}
