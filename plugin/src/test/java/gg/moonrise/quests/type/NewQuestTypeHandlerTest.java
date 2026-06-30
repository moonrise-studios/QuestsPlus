package gg.moonrise.quests.type;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import gg.moonrise.quests.core.service.QuestResetService;
import gg.moonrise.quests.core.service.QuestService;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.model.QuestDefinition;
import gg.moonrise.quests.sdk.model.QuestTypes;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewQuestTypeHandlerTest {

    @Test
    public void milkMobGeneratesCanonicalMilkableMobQuest() {
        MilkMobGoalHandler handler = new MilkMobGoalHandler(mock(QuestService.class), mock(QuestResetService.class));
        QuestDefinition definition = definition("milk-run", QuestTypes.MILK_MOB, Map.of(
                "mob-type", List.of("cow", "GOAT", "MOOSHROOM"),
                "goal-amount", List.of("5")
        ));

        handler.validateDefinition(definition);
        GeneratedQuest quest = handler.createGeneratedQuest(definition, UUID.randomUUID(), "daily", Map.of(
                "mob-type", "cow",
                "goal-amount", "5"
        ));

        assertEquals("COW", quest.variables().get("mob-type"));
        assertEquals(5, quest.goalAmount());
        assertThrows(IllegalArgumentException.class, () -> handler.validateDefinition(definition("bad-milk", QuestTypes.MILK_MOB, Map.of(
                "mob-type", List.of("ZOMBIE"),
                "goal-amount", List.of("5")
        ))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void milkMobEventProgressesMatchingMilkableMobType() {
        QuestService questService = mock(QuestService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Entity cow = mock(Entity.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        MilkMobGoalHandler handler = new MilkMobGoalHandler(questService, resetService);
        ArgumentCaptor<Predicate<GeneratedQuest>> matcher = ArgumentCaptor.forClass(Predicate.class);

        when(resetService.currentResetKey()).thenReturn("daily");
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(cow);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(cow.getType()).thenReturn(EntityType.COW);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.BUCKET));

        handler.onPlayerInteractEntity(event);

        verify(questService).progressMatching(eq(player), eq(QuestTypes.MILK_MOB), eq("daily"), matcher.capture());
        assertTrue(matcher.getValue().test(quest(QuestTypes.MILK_MOB, Map.of("mob-type", "COW"))));
        assertFalse(matcher.getValue().test(quest(QuestTypes.MILK_MOB, Map.of("mob-type", "GOAT"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void throwItemEventProgressesMatchingThrownItemType() {
        QuestService questService = mock(QuestService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        Player player = mock(Player.class);
        PlayerLaunchProjectileEvent event = mock(PlayerLaunchProjectileEvent.class);
        ThrowItemGoalHandler handler = new ThrowItemGoalHandler(questService, resetService);
        ArgumentCaptor<Predicate<GeneratedQuest>> matcher = ArgumentCaptor.forClass(Predicate.class);

        when(resetService.currentResetKey()).thenReturn("daily");
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemStack()).thenReturn(new ItemStack(Material.ENDER_PEARL));

        handler.onPlayerLaunchProjectile(event);

        verify(questService).progressMatching(eq(player), eq(QuestTypes.THROW_ITEM), eq("daily"), matcher.capture());
        assertTrue(matcher.getValue().test(quest(QuestTypes.THROW_ITEM, Map.of("item-type", "minecraft:ender_pearl"))));
        assertFalse(matcher.getValue().test(quest(QuestTypes.THROW_ITEM, Map.of("item-type", "minecraft:snowball"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void villagerTradeEventProgressesGenericTradeQuest() {
        QuestService questService = mock(QuestService.class);
        QuestResetService resetService = mock(QuestResetService.class);
        Player player = mock(Player.class);
        PlayerTradeEvent event = mock(PlayerTradeEvent.class);
        VillagerTradeGoalHandler handler = new VillagerTradeGoalHandler(questService, resetService);
        ArgumentCaptor<Predicate<GeneratedQuest>> matcher = ArgumentCaptor.forClass(Predicate.class);

        when(resetService.currentResetKey()).thenReturn("daily");
        when(event.getPlayer()).thenReturn(player);

        handler.onPlayerTrade(event);

        verify(questService).progressMatching(eq(player), eq(QuestTypes.VILLAGER_TRADE), eq("daily"), matcher.capture());
        assertTrue(matcher.getValue().test(quest(QuestTypes.VILLAGER_TRADE, Map.of())));
    }

    private static QuestDefinition definition(String id, gg.moonrise.quests.sdk.model.QuestType type, Map<String, List<String>> selectorValues) {
        return new QuestDefinition(
                id,
                type,
                true,
                "easy",
                "Easy",
                "Quest",
                List.of(),
                Map.of(),
                selectorValues,
                null,
                List.of()
        );
    }

    private static GeneratedQuest quest(gg.moonrise.quests.sdk.model.QuestType type, Map<String, String> variables) {
        return new GeneratedQuest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "daily",
                "definition",
                type,
                "easy",
                "Easy",
                "Quest",
                List.of(),
                variables,
                10,
                0,
                false
        );
    }
}
