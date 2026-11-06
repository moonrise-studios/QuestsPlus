package gg.moonrise.quests.core.service;

import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.config.DailyFile;
import gg.moonrise.quests.config.PremiumQuestMenu;
import gg.moonrise.quests.config.PremiumQuestsFile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestMenuLayoutTest {

    @Test
    public void defaultLayoutKeepsUtilityButtonSlotsOutOfTheContentSlots() {
        QuestMenuLayout layout = QuestMenuLayout.of(new Config());

        assertEquals(27, layout.totalSlots());
        assertEquals(Set.of(15, 18, 22, 26), layout.reservedSlots());
        assertEquals(List.of(10, 11, 12, 13, 14, 16), layout.contentSlots());
        assertEquals(6, layout.capacity());
        assertEquals(3, layout.dailyQuestSlots());
        assertFalse(layout.isPremiumSlot(2));
        assertTrue(layout.isPremiumSlot(3));
    }

    @Test
    public void disabledButtonsDoNotReserveTheirSlots() throws ReflectiveOperationException {
        Config config = new Config();
        setField(config.getMenu().getMilestoneButton(), "enabled", false);
        setField(config.getMenu().getStreakButton(), "enabled", false);
        setField(config.getMenu().getResetButton(), "enabled", false);
        setField(config.getMenu(), "contentSlots", List.of(18, 22, 26, 15));

        QuestMenuLayout layout = QuestMenuLayout.of(config);

        assertEquals(Set.of(15), layout.reservedSlots());
        assertEquals(List.of(18, 22, 26), layout.contentSlots());
    }

    @Test
    public void unusableContentSlotsFallBackToEveryFreeSlot() throws ReflectiveOperationException {
        Config config = new Config();
        setField(config.getMenu(), "contentSlots", List.of(15, 27, -1, 18));

        QuestMenuLayout layout = QuestMenuLayout.of(config);

        List<Integer> expected = IntStream.range(0, 27)
                .filter(slot -> !Set.of(15, 18, 22, 26).contains(slot))
                .boxed()
                .toList();
        assertEquals(expected, layout.contentSlots());
    }

    @Test
    public void duplicateContentSlotsAreCountedOnceAndClampDailySlots() throws ReflectiveOperationException {
        Config config = new Config();
        setField(config.getMenu(), "contentSlots", List.of(10, 11, 10));

        QuestMenuLayout layout = QuestMenuLayout.of(config);

        assertEquals(List.of(10, 11), layout.contentSlots());
        assertEquals(2, layout.dailyQuestSlots());
        assertEquals(0, layout.visiblePremiumSlots());
        assertEquals(2, layout.totalVisibleQuestSlots());
    }

    @Test
    public void visiblePremiumSlotsUseTheHighestLimitClampedToFreeCapacity() {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("vip", 1);
        limits.put("mvp", 5);
        PremiumQuestsFile premium = new PremiumQuestsFile(limits, new PremiumQuestMenu(Map.of()));
        Config config = Config.compose(null, new DailyFile(3), null, null, null, null, premium);

        QuestMenuLayout layout = QuestMenuLayout.of(config);

        assertEquals(3, layout.visiblePremiumSlots());
        assertEquals(6, layout.totalVisibleQuestSlots());
    }

    @Test
    public void resetButtonSlotIsReservedWheneverTheButtonIsEnabled() throws ReflectiveOperationException {
        Config config = new Config();
        // Content slots that cover the reset button: it stays reserved even if no currency ends up showing it.
        setField(config.getMenu(), "contentSlots", List.of(17, 18, 19));

        QuestMenuLayout layout = QuestMenuLayout.of(config);

        assertTrue(layout.reservedSlots().contains(18));
        assertEquals(List.of(17, 19), layout.contentSlots());
    }

    @Test
    public void globalQuestSlotIsClampedToTheLargestMenuAndReservedOnlyIfItFits() throws ReflectiveOperationException {
        Config config = new Config();
        setField(config.getGlobalQuests().getMenu(), "slot", 60);

        QuestMenuLayout small = QuestMenuLayout.of(config);
        setField(config.getMenu(), "rows", 6);
        QuestMenuLayout large = QuestMenuLayout.of(config);

        assertEquals(53, QuestMenuLayout.globalQuestSlot(config));
        assertEquals(53, small.globalQuestSlot());
        assertFalse(small.reservedSlots().contains(53));
        assertEquals(3, small.rows());
        assertEquals(6, large.rows());
        assertTrue(large.reservedSlots().contains(53));
    }

    @Test
    public void missingGlobalQuestMenuUsesTheDefaultSlot() throws ReflectiveOperationException {
        Config config = new Config();
        setField(config.getGlobalQuests(), "menu", null);

        assertEquals(15, QuestMenuLayout.of(config).globalQuestSlot());
    }

    @Test
    public void questServiceComputesTheLayoutOncePerConfigInstance() {
        Config first = new Config();
        Config reloaded = new Config();
        ConfigProvider configProvider = mock(ConfigProvider.class);
        when(configProvider.get()).thenReturn(first);
        QuestServices service = QuestServices.create(
                configProvider,
                mock(QuestDefinitionService.class),
                mock(QuestRepository.class),
                mock(QuestStreakService.class),
                mock(GlobalQuestService.class),
                mock(QuestProgressIndicatorService.class)
        );

        QuestMenuLayout layout = service.slots().menuLayout();
        assertSame(layout, service.slots().menuLayout());

        when(configProvider.get()).thenReturn(reloaded);
        QuestMenuLayout reloadedLayout = service.slots().menuLayout();
        assertNotSame(layout, reloadedLayout);
        assertSame(reloadedLayout, service.slots().menuLayout());
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
