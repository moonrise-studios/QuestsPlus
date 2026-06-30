package gg.moonrise.quests.sdk.currency;

import java.util.List;
import java.util.Objects;

/**
 * Menu button template for a QuestsPlus currency.
 *
 * @param slot zero-based inventory slot
 * @param material Bukkit material name
 * @param name MiniMessage item name
 * @param lore MiniMessage item lore
 */
public record QuestCurrencyButton(int slot, String material, String name, List<String> lore) {

    /**
     * Creates a button template and defensively copies lore lines.
     *
     * @param slot zero-based inventory slot
     * @param material Bukkit material name
     * @param name MiniMessage item name
     * @param lore MiniMessage item lore
     */
    public QuestCurrencyButton {
        material = Objects.requireNonNullElse(material, "PAPER").trim();
        if (material.isBlank()) {
            material = "PAPER";
        }
        name = Objects.requireNonNullElse(name, "<gold>Quest Reset Currency");
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    /**
     * Creates a button template.
     *
     * @param slot zero-based inventory slot
     * @param material Bukkit material name
     * @param name MiniMessage item name
     * @param lore MiniMessage item lore
     * @return a button template
     */
    public static QuestCurrencyButton of(int slot, String material, String name, List<String> lore) {
        return new QuestCurrencyButton(slot, material, name, lore);
    }
}
