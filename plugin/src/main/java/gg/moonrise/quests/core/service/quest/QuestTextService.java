package gg.moonrise.quests.core.service.quest;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.entity.PlayerQuestState;
import gg.moonrise.quests.entity.QuestDifficulty;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.sdk.util.QuestPlaceholders;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;
import gg.moonrise.quests.config.ConfigProvider;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fills quest {@code <token>} placeholders for menus and reward commands, and sends the quest progress and
 * completion count summaries.
 */
@SpringComponent
@RequiredArgsConstructor
public class QuestTextService {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestSlotService slotService;

    public void sendProgress(CommandSender sender, PlayerQuestState state) {
        configProvider.get().getMessages().getProgressHeader().send(sender, Placeholder.unparsed("reset_key", state.resetKey()));
        for (GeneratedQuest quest : state.quests()) {
            if (sender instanceof Player player && !slotService.canAccessQuest(player, quest)) {
                continue;
            }
            configProvider.get().getMessages().getProgressLine().send(
                    sender,
                    Placeholder.unparsed("quest_display_name", QuestTokens.stripTags(quest.displayName())),
                    Placeholder.unparsed("progress", QuestNumberFormatter.format(quest.progress())),
                    Placeholder.unparsed("goal_amount", QuestNumberFormatter.format(quest.goalAmount())),
                    Placeholder.unparsed("status", quest.completed() ? "completed" : "active")
            );
        }
    }

    public void sendCompletedCount(CommandSender sender, PlayerQuestState state) {
        configProvider.get().getMessages().getCompletedCount().send(sender, Placeholder.unparsed("quests_completed", QuestNumberFormatter.format(state.questsCompleted())));
        Map<String, Integer> remainingCounts = new LinkedHashMap<>(state.difficultyCompletions());
        for (QuestDifficulty difficulty : definitionService.difficulties()) {
            Integer count = remainingCounts.remove(difficulty.id());
            int completed = count == null ? 0 : count;
            sendCompletedDifficultyLine(sender, difficulty.id(), difficulty.displayName(), completed);
        }
        for (Map.Entry<String, Integer> entry : remainingCounts.entrySet()) {
            sendCompletedDifficultyLine(sender, entry.getKey(), entry.getKey(), entry.getValue());
        }
    }

    public String replaceQuestTokens(String template, GeneratedQuest quest, Player player) {
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        QuestTokens.putQuestNumbers(values, quest, true);
        return QuestTokens.apply(template, values);
    }

    /**
     * Fills the tokens of a reward command. Numbers are left unformatted so commands receive plain values.
     */
    public String replaceQuestRewardTokens(String template, GeneratedQuest quest, Player player) {
        Map<String, String> values = commonPlaceholders(quest, player);
        values.putAll(definitionService.variablePlaceholders(quest));
        QuestTokens.putQuestNumbers(values, quest, false);
        return QuestTokens.apply(template, values);
    }

    public String replaceRawVariableDisplayValues(String template, GeneratedQuest quest) {
        String output = template == null ? "" : template;
        Map<String, String> displayValues = definitionService.variablePlaceholders(quest);
        for (Map.Entry<String, String> entry : quest.variables().entrySet()) {
            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            String displayValue = displayValues.get(entry.getKey());
            if (displayValue == null) {
                displayValue = displayValues.get(QuestPlaceholders.placeholderKey(entry.getKey()));
            }
            if (displayValue == null || displayValue.equals(rawValue)) {
                continue;
            }
            output = output.replace(rawValue, displayValue);
        }
        return output;
    }

    private void sendCompletedDifficultyLine(CommandSender sender, String difficultyId, String difficultyDisplayName, int questsCompleted) {
        configProvider.get().getMessages().getCompletedDifficultyLine().send(
                sender,
                Placeholder.unparsed("quest_difficulty", QuestTokens.stripTags(difficultyDisplayName)),
                Placeholder.unparsed("difficulty_id", difficultyId),
                Placeholder.unparsed("quests_completed", QuestNumberFormatter.format(questsCompleted))
        );
    }

    private Map<String, String> commonPlaceholders(GeneratedQuest quest, Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", QuestTokens.stripTags(quest.displayName()));
        values.put("quest_difficulty", quest.difficultyDisplayName());
        values.put("quest_difficulty_id", quest.difficultyId());
        QuestTokens.putQuestNumbers(values, quest, true);
        return values;
    }
}
