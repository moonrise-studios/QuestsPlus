package gg.moonrise.quests.indicator;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.ConfigProvider;
import gg.moonrise.quests.util.QuestNumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SpringComponent
@RequiredArgsConstructor
public class ChatQuestProgressIndicator implements QuestProgressIndicator, Disableable {

    private static final String TYPE = "CHAT";

    private final ConfigProvider configProvider;
    private final Map<UUID, PendingBatch> batches = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void show(QuestProgressIndicatorContext context) {
        Config.ChatProgressIndicator config = config();
        if (!config.isEnabled()) {
            return;
        }

        Player player = context.player();
        UUID playerId = player.getUniqueId();
        PendingBatch batch = batches.computeIfAbsent(playerId, ignored -> new PendingBatch(scheduleFlush(player)));
        batch.record(context);
    }

    @Override
    public void clearAll() {
        for (PendingBatch batch : batches.values()) {
            batch.flushTask().cancel();
        }
        batches.clear();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    private ScheduledTask scheduleFlush(Player player) {
        long intervalSeconds = Math.max(1, config().getIntervalSeconds());
        return Scheduler.entity(player).runDelayed(task -> flush(player.getUniqueId()), Duration.ofSeconds(intervalSeconds));
    }

    private void flush(UUID playerId) {
        PendingBatch batch = batches.remove(playerId);
        if (batch == null) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        Config.ChatProgressIndicator config = config();
        Map<BatchKey, BatchEntry> entries = batch.entries();
        if (entries.isEmpty()) {
            return;
        }

        String header = config.getHeader();
        if (header != null && !header.isBlank()) {
            player.sendMessage(miniMessage.deserialize(header));
        }
        for (BatchEntry entry : entries.values()) {
            player.sendMessage(miniMessage.deserialize(applyTokens(config.getLine(), entry.placeholders())));
        }
    }

    private Config.ChatProgressIndicator config() {
        Config.ProgressIndicators indicators = configProvider.get().getProgressIndicators();
        return indicators == null || indicators.getChat() == null
                ? new Config.ChatProgressIndicator()
                : indicators.getChat();
    }

    private String applyTokens(String template, Map<String, String> values) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }

    private record BatchKey(QuestProgressIndicatorContext.Scope scope, UUID instanceId) {
    }

    private record BatchEntry(Map<String, String> placeholders) {
    }

    private static final class PendingBatch {

        private final ScheduledTask flushTask;
        private final Map<BatchKey, BatchEntry> entries = new LinkedHashMap<>();

        private PendingBatch(ScheduledTask flushTask) {
            this.flushTask = flushTask;
        }

        private ScheduledTask flushTask() {
            return flushTask;
        }

        private synchronized void record(QuestProgressIndicatorContext context) {
            BatchKey key = new BatchKey(context.scope(), context.quest().instanceId());
            BatchEntry existing = entries.get(key);
            int previousProgress = existing == null
                    ? context.previousProgress()
                    : parseFormattedInt(existing.placeholders().get("previous_progress"), context.previousProgress());
            Map<String, String> values = new LinkedHashMap<>(context.placeholders());
            values.put("previous_progress", QuestNumberFormatter.format(previousProgress));
            values.put("previous-progress", QuestNumberFormatter.format(previousProgress));
            values.put("progress", QuestNumberFormatter.format(context.quest().progress()));
            values.put("goal_amount", QuestNumberFormatter.format(context.quest().goalAmount()));
            values.put("goal-amount", QuestNumberFormatter.format(context.quest().goalAmount()));
            entries.put(key, new BatchEntry(Map.copyOf(values)));
        }

        private synchronized Map<BatchKey, BatchEntry> entries() {
            return new LinkedHashMap<>(entries);
        }

        private static int parseFormattedInt(String raw, int fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw.replace(",", ""));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }
}
