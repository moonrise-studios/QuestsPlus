package gg.moonrise.quests.indicator;

import net.kyori.adventure.text.Component;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import org.bukkit.entity.Player;

import java.util.Map;

public record QuestProgressIndicatorContext(
        Player player,
        Scope scope,
        GeneratedQuest quest,
        GlobalQuestState globalState,
        int previousProgress,
        double progress,
        Map<String, String> placeholders,
        Component title
) {

    public QuestProgressIndicatorContext {
        placeholders = Map.copyOf(placeholders);
    }

    public enum Scope {
        PERSONAL,
        GLOBAL
    }
}
