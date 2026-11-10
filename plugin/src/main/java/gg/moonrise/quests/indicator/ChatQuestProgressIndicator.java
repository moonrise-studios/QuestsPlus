package gg.moonrise.quests.indicator;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import gg.moonrise.quests.config.indicator.ChatProgressIndicator;
import gg.moonrise.quests.config.indicator.ProgressIndicators;
import gg.moonrise.quests.config.ConfigProvider;
import gg.moonrise.quests.util.QuestNumberFormatter;
import gg.moonrise.quests.util.QuestTokens;
import gg.moonrise.quests.core.service.indicator.QuestProgressIndicator;
import gg.moonrise.quests.core.service.indicator.QuestProgressIndicatorContext;
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
    public boolean isEnabled(ProgressIndicators config) {
        return settings(config).isEnabled();
    }

    @Override
    public String titleTemplate(ProgressIndicators config) {
        return settings(config).getLine();
    }

    @Override
    public void show(QuestProgressIndicatorContext context) {
        ChatProgressIndicator config = config();
        if (!config.isEnabled()) {
            return;
        }

        Player player = context.player();
        UUID playerId = player.getUniqueId();
        PendingBatch created = new PendingBatch();
        PendingBatch existing = batches.putIfAbsent(playerId, created);
        PendingBatch batch = existing == null ? created : existing;
        batch.record(context);
        if (existing != null) {
            return;
        }

        ScheduledTask flushTask = scheduleFlush(player, created);
        if (flushTask == null) {
            // The player's scheduler is already retired, so the flush can never run.
            batches.remove(playerId, created);
            return;
        }
        created.flushTask(flushTask);
    }

    @Override
    public void clear(UUID playerId) {
        PendingBatch batch = batches.remove(playerId);
        if (batch != null) {
            batch.cancel();
        }
    }

    @Override
    public void clearAll() {
        for (PendingBatch batch : batches.values()) {
            batch.cancel();
        }
        batches.clear();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    private ScheduledTask scheduleFlush(Player player, PendingBatch batch) {
        UUID playerId = player.getUniqueId();
        long intervalSeconds = Math.max(1, config().getIntervalSeconds());
        return Scheduler.entity(player).runDelayed(
                task -> flush(playerId, batch),
                () -> batches.remove(playerId, batch),
                Duration.ofSeconds(intervalSeconds)
        );
    }

    private void flush(UUID playerId, PendingBatch batch) {
        if (!batches.remove(playerId, batch)) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        ChatProgressIndicator config = config();
        Map<BatchKey, BatchEntry> entries = batch.entries();
        if (entries.isEmpty()) {
            return;
        }

        String header = config.getHeader();
        if (header != null && !header.isBlank()) {
            player.sendMessage(miniMessage.deserialize(header));
        }
        for (BatchEntry entry : entries.values()) {
            player.sendMessage(miniMessage.deserialize(QuestTokens.apply(config.getLine(), entry.placeholders())));
        }
    }

    private ChatProgressIndicator config() {
        ProgressIndicators indicators = configProvider.get().getProgressIndicators();
        return indicators == null ? new ChatProgressIndicator() : settings(indicators);
    }

    private ChatProgressIndicator settings(ProgressIndicators config) {
        return config.getChat() == null ? new ChatProgressIndicator() : config.getChat();
    }

    private record BatchKey(QuestProgressIndicatorContext.Scope scope, UUID instanceId) {
    }

    private record BatchEntry(Map<String, String> placeholders) {
    }

    private static final class PendingBatch {

        private final Map<BatchKey, BatchEntry> entries = new LinkedHashMap<>();
        private ScheduledTask flushTask;
        private boolean cancelled;

        private synchronized void flushTask(ScheduledTask flushTask) {
            if (cancelled) {
                flushTask.cancel();
                return;
            }
            this.flushTask = flushTask;
        }

        private synchronized void cancel() {
            cancelled = true;
            if (flushTask != null) {
                flushTask.cancel();
            }
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
            QuestTokens.putQuestNumbers(values, context.quest(), true);
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
