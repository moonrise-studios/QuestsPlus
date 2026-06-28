package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.indicator.QuestProgressIndicator;
import gg.moonrise.quests.indicator.QuestProgressIndicatorContext;
import gg.moonrise.quests.model.GlobalQuestState;
import gg.moonrise.quests.sdk.model.GeneratedQuest;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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

    private static final ThreadLocal<DecimalFormat> PERCENT_FORMATTER = ThreadLocal.withInitial(() -> new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(java.util.Locale.US)));

    private final ConfigProvider configProvider;
    private final QuestDefinitionService definitionService;
    private final QuestIndicatorPreferenceService indicatorPreferenceService;
    private final List<QuestProgressIndicator> indicators;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public void showPersonal(Player player, GeneratedQuest quest) {
        showPersonal(player, quest, quest.progress());
    }

    public void showPersonal(Player player, GeneratedQuest quest, int previousProgress) {
        Config.ProgressIndicators config = config();
        if (!enabled(config)) {
            return;
        }

        Map<String, String> values = personalPlaceholders(player, quest);
        showConfigured(player, QuestProgressIndicatorContext.Scope.PERSONAL, quest, null, previousProgress, values, progress(quest), config);
    }

    public void showGlobal(Player player, GlobalQuestState state) {
        showGlobal(player, state, state.quest().progress());
    }

    public void showGlobal(Player player, GlobalQuestState state, int previousProgress) {
        Config.ProgressIndicators config = config();
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
            Config.ProgressIndicators config
    ) {
        List<String> selectedTypes = selectedTypes(player, scope, config);
        for (QuestProgressIndicator indicator : indicators) {
            if (!selectedTypes.contains(indicator.type())) {
                continue;
            }
            Component title = miniMessage.deserialize(applyTokens(titleTemplate(indicator.type(), config), values));
            indicator.show(new QuestProgressIndicatorContext(player, scope, quest, globalState, previousProgress, progress, values, title));
        }
    }

    public List<String> availableIndicatorTypes() {
        Config.ProgressIndicators config = config();
        if (!enabled(config)) {
            return List.of();
        }
        return availableIndicatorTypes(config);
    }

    private List<String> selectedTypes(Player player, QuestProgressIndicatorContext.Scope scope, Config.ProgressIndicators config) {
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

    private List<String> availableIndicatorTypes(Config.ProgressIndicators config) {
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (QuestProgressIndicator indicator : indicators) {
            if (indicatorEnabled(indicator.type(), config)) {
                available.add(indicator.type());
            }
        }
        return new ArrayList<>(available);
    }

    private boolean indicatorEnabled(String type, Config.ProgressIndicators config) {
        if ("BOSS_BAR".equals(type)) {
            return bossBar(config).isEnabled();
        }
        if ("ACTION_BAR".equals(type)) {
            return actionBar(config).isEnabled();
        }
        if ("CHAT".equals(type)) {
            return chat(config).isEnabled();
        }
        return true;
    }

    private boolean enabled(Config.ProgressIndicators config) {
        return config != null && config.isEnabled();
    }

    private Config.ProgressIndicators config() {
        Config.ProgressIndicators indicatorsConfig = configProvider.get().getProgressIndicators();
        return indicatorsConfig == null ? new Config.ProgressIndicators() : indicatorsConfig;
    }

    private Config.BossBarProgressIndicator bossBar(Config.ProgressIndicators config) {
        return config.getBossBar() == null ? new Config.BossBarProgressIndicator() : config.getBossBar();
    }

    private Config.ActionBarProgressIndicator actionBar(Config.ProgressIndicators config) {
        return config.getActionBar() == null ? new Config.ActionBarProgressIndicator() : config.getActionBar();
    }

    private Config.ChatProgressIndicator chat(Config.ProgressIndicators config) {
        return config.getChat() == null ? new Config.ChatProgressIndicator() : config.getChat();
    }

    private String titleTemplate(String type, Config.ProgressIndicators config) {
        if ("ACTION_BAR".equals(type)) {
            return actionBar(config).getTitle();
        }
        if ("CHAT".equals(type)) {
            return chat(config).getLine();
        }
        return bossBar(config).getTitle();
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
        Rank rank = rank(state, player.getUniqueId());
        Map<String, String> values = commonQuestPlaceholders(player, quest);
        values.putAll(definitionService.variablePlaceholders(quest));
        putQuestNumbers(values, quest);
        values.put("global_progress", QuestNumberFormatter.format(quest.progress()));
        values.put("global_goal_amount", QuestNumberFormatter.format(quest.goalAmount()));
        values.put("global_percent", percent(quest.progress(), quest.goalAmount()));
        values.put("contribution", QuestNumberFormatter.format(contribution));
        values.put("contribution_percent", percent(contribution, Math.max(1, quest.progress())));
        values.put("global_rank", rank.rank() <= 0 ? "-" : QuestNumberFormatter.format(rank.rank()));
        values.put("global_participants", QuestNumberFormatter.format(rank.participants()));
        values.put("global_time_remaining", timeRemaining(state.endsAt()));
        values.put("quest_type", quest.type().key());
        values.put("period_key", state.periodKey());
        return values;
    }

    private Map<String, String> commonQuestPlaceholders(Player player, GeneratedQuest quest) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("quest_id", quest.definitionId());
        values.put("quest_display_name", stripMiniMessageFallback(quest.displayName()));
        values.put("quest_difficulty", quest.difficultyDisplayName());
        values.put("quest_difficulty_id", quest.difficultyId());
        putQuestNumbers(values, quest);
        return values;
    }

    private void putQuestNumbers(Map<String, String> values, GeneratedQuest quest) {
        values.put("goal_amount", QuestNumberFormatter.format(quest.goalAmount()));
        values.put("goal-amount", QuestNumberFormatter.format(quest.goalAmount()));
        values.put("progress", QuestNumberFormatter.format(quest.progress()));
        values.put("percent", progressPercent(quest));
    }

    private String applyTokens(String template, Map<String, String> values) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private double progress(GeneratedQuest quest) {
        if (quest.goalAmount() <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, quest.progress() / (double) quest.goalAmount()));
    }

    private String percent(int value, int max) {
        if (max <= 0) {
            return "0";
        }
        return QuestNumberFormatter.format(Math.min(100, (int) Math.floor((value * 100.0D) / max)));
    }

    private String progressPercent(GeneratedQuest quest) {
        if (quest.goalAmount() <= 0) {
            return "0.00%";
        }
        double percent = Math.max(0.0D, Math.min(100.0D, (quest.progress() * 100.0D) / quest.goalAmount()));
        return PERCENT_FORMATTER.get().format(percent) + "%";
    }

    private String timeRemaining(LocalDateTime endsAt) {
        long remainingMillis = Duration.between(LocalDateTime.now(), endsAt).toMillis();
        long ceilMinutes = remainingMillis <= 0 ? 0 : (remainingMillis + 59_999) / 60_000;
        if (ceilMinutes < 60) {
            return "Ends in " + QuestNumberFormatter.format(ceilMinutes) + "m";
        }
        long ceilHours = (ceilMinutes + 59) / 60;
        if (ceilHours < 24) {
            return "Ends in " + QuestNumberFormatter.format(ceilHours) + "h";
        }
        long ceilDays = (ceilHours + 23) / 24;
        return "Ends in " + QuestNumberFormatter.format(ceilDays) + "d";
    }

    private Rank rank(GlobalQuestState state, UUID playerId) {
        List<Map.Entry<UUID, Integer>> ranked = state.contributions().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(entry -> entry.getKey().toString()))
                .toList();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            if (entry.getKey().equals(playerId)) {
                return new Rank(rank, ranked.size());
            }
            rank++;
        }
        return new Rank(0, ranked.size());
    }

    private String stripMiniMessageFallback(String input) {
        return input == null ? "" : input.replaceAll("<[^>]+>", "").trim();
    }

    private record Rank(int rank, int participants) {
    }
}
