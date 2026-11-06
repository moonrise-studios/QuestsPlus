package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import gg.moonrise.quests.config.ProgressIndicators;
import gg.moonrise.quests.indicator.QuestProgressIndicator;
import gg.moonrise.quests.indicator.QuestProgressIndicatorContext;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.util.QuestSchedules;
import gg.moonrise.quests.util.QuestTokens;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringComponent
@RequiredArgsConstructor
public class QuestProgressIndicatorService {

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestIndicatorPreferenceService indicatorPreferenceService;
    private final List<QuestProgressIndicator> indicators;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void showPersonal(Player player, GeneratedQuest quest, int previousProgress) {
        ProgressIndicators config = config();
        if (!enabled(config)) {
            return;
        }

        Map<String, String> values = personalPlaceholders(player, quest);
        showConfigured(player, QuestProgressIndicatorContext.Scope.PERSONAL, quest, null, previousProgress, values, progress(quest), config);
    }

    public void showGlobal(Player player, GlobalQuestState state, int previousProgress) {
        ProgressIndicators config = config();
        if (!enabled(config)) {
            return;
        }

        Map<String, String> values = globalPlaceholders(player, state);
        showConfigured(player, QuestProgressIndicatorContext.Scope.GLOBAL, state.quest(), state, previousProgress, values, progress(state.quest()), config);
    }

    private void showConfigured(
            Player player,
            QuestProgressIndicatorContext.Scope scope,
            GeneratedQuest quest,
            GlobalQuestState globalState,
            int previousProgress,
            Map<String, String> values,
            double progress,
            ProgressIndicators config
    ) {
        List<String> selectedTypes = selectedTypes(player, scope, config);
        for (QuestProgressIndicator indicator : indicators) {
            if (!selectedTypes.contains(indicator.type())) {
                continue;
            }
            Component title = miniMessage.deserialize(QuestTokens.apply(indicator.titleTemplate(config), values));
            indicator.show(new QuestProgressIndicatorContext(player, scope, quest, globalState, previousProgress, progress, values, title));
        }
    }

    public void clearPlayer(UUID playerId) {
        for (QuestProgressIndicator indicator : indicators) {
            indicator.clear(playerId);
        }
    }

    public List<String> availableIndicatorTypes() {
        ProgressIndicators config = config();
        if (!enabled(config)) {
            return List.of();
        }
        return availableIndicatorTypes(config);
    }

    private List<String> selectedTypes(Player player, QuestProgressIndicatorContext.Scope scope, ProgressIndicators config) {
        List<String> available = availableIndicatorTypes(config);
        if (available.isEmpty()) {
            return List.of();
        }

        QuestIndicatorPreferenceService.Scope preferenceScope = scope == QuestProgressIndicatorContext.Scope.GLOBAL
                ? QuestIndicatorPreferenceService.Scope.GLOBAL
                : QuestIndicatorPreferenceService.Scope.PERSONAL;
        String preferred = indicatorPreferenceService.cachedPreference(player.getUniqueId(), preferenceScope).orElse(null);
        if (QuestIndicatorPreferenceService.OFF.equals(preferred)) {
            return List.of();
        }
        if (preferred != null && available.contains(preferred)) {
            return List.of(preferred);
        }
        if (available.contains(QuestIndicatorPreferenceService.DEFAULT_INDICATOR)) {
            return List.of(QuestIndicatorPreferenceService.DEFAULT_INDICATOR);
        }
        return List.of(available.getFirst());
    }

    private List<String> availableIndicatorTypes(ProgressIndicators config) {
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (QuestProgressIndicator indicator : indicators) {
            if (indicator.isEnabled(config)) {
                available.add(indicator.type());
            }
        }
        return new ArrayList<>(available);
    }

    private boolean enabled(ProgressIndicators config) {
        return config != null && config.isEnabled();
    }

    private ProgressIndicators config() {
        ProgressIndicators indicatorsConfig = configProvider.get().getProgressIndicators();
        return indicatorsConfig == null ? new ProgressIndicators() : indicatorsConfig;
    }

    private Map<String, String> personalPlaceholders(Player player, GeneratedQuest quest) {
        Map<String, String> values = commonQuestPlaceholders(player, quest);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest);
        return values;
    }

    private Map<String, String> globalPlaceholders(Player player, GlobalQuestState state) {
        GeneratedQuest quest = state.quest();
        int contribution = state.contribution(player.getUniqueId());
        GlobalQuestState.Rank rank = state.rank(player.getUniqueId());
        Map<String, String> values = commonQuestPlaceholders(player, quest);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest);
        values.put("global_progress", QuestTokens.number(quest.progress(), true));
        values.put("global_goal_amount", QuestTokens.number(quest.goalAmount(), true));
        values.put("global_percent", QuestTokens.percent(quest.progress(), quest.goalAmount(), true));
        values.put("contribution", QuestTokens.number(contribution, true));
        values.put("contribution_percent", QuestTokens.percent(contribution, Math.max(1, quest.progress()), true));
        values.put("global_rank", rank.rank() <= 0 ? "-" : QuestTokens.number(rank.rank(), true));
        values.put("global_participants", QuestTokens.number(rank.participants(), true));
        values.put("global_time_remaining", QuestSchedules.formatRemaining("Ends in ", Duration.between(LocalDateTime.now(), state.endsAt()), true));
        values.put("quest_type", quest.type().key());
        values.put("period_key", state.periodKey());
        return values;
    }

    private Map<String, String> commonQuestPlaceholders(Player player, GeneratedQuest quest) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", QuestTokens.stripTags(quest.displayName()));
        values.put("quest_difficulty", quest.difficultyDisplayName());
        values.put("quest_difficulty_id", quest.difficultyId());
        putQuestNumbers(values, quest);
        return values;
    }

    private void putQuestNumbers(Map<String, String> values, GeneratedQuest quest) {
        QuestTokens.putQuestNumbers(values, quest, true);
        values.put("percent", QuestTokens.progressPercent(quest));
    }

    private double progress(GeneratedQuest quest) {
        if (quest.goalAmount() <= 0) {
            return 0.0D;
        }
        return Math.clamp(quest.progress() / (double) quest.goalAmount(), 0.0D, 1.0D);
    }
}
