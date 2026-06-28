package gg.moonrise.quests.indicator;

import gg.moonrise.engine.paper.scheduler.Scheduler;
import gg.moonrise.moss.spring.Disableable;
import gg.moonrise.moss.spring.SpringComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.bossbar.BossBar;
import gg.moonrise.quests.config.Config;
import gg.moonrise.quests.core.service.ConfigProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "QuestsPlus")
@SpringComponent
@RequiredArgsConstructor
public class BossBarQuestProgressIndicator implements QuestProgressIndicator, Disableable {

    private static final String TYPE = "BOSS_BAR";

    private final ConfigProvider configProvider;
    private final Map<UUID, ActiveBossBar> activeBars = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void show(QuestProgressIndicatorContext context) {
        Player player = context.player();
        Scheduler.entity(player).run(task -> {
            if (!player.isOnline()) {
                return;
            }

            hide(player);

            Config.BossBarProgressIndicator config = config();
            BossBar bossBar = BossBar.bossBar(
                    context.title(),
                    (float) Math.max(0.0D, Math.min(1.0D, context.progress())),
                    color(config.getColor()),
                    overlay(config.getOverlay())
            );
            player.showBossBar(bossBar);

            long durationSeconds = Math.max(1, config.getDurationSeconds());
            ScheduledTask hideTask = Scheduler.entity(player).runDelayed(delayedTask -> {
                ActiveBossBar active = activeBars.remove(player.getUniqueId());
                if (active != null && player.isOnline()) {
                    player.hideBossBar(active.bossBar());
                }
            }, Duration.ofSeconds(durationSeconds));
            activeBars.put(player.getUniqueId(), new ActiveBossBar(bossBar, hideTask));
        });
    }

    @Override
    public void clearAll() {
        for (Map.Entry<UUID, ActiveBossBar> entry : activeBars.entrySet()) {
            ActiveBossBar active = entry.getValue();
            active.hideTask().cancel();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(active.bossBar());
            }
        }
        activeBars.clear();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    private void hide(Player player) {
        ActiveBossBar active = activeBars.remove(player.getUniqueId());
        if (active == null) {
            return;
        }
        active.hideTask().cancel();
        player.hideBossBar(active.bossBar());
    }

    private Config.BossBarProgressIndicator config() {
        Config.ProgressIndicators indicators = configProvider.get().getProgressIndicators();
        return indicators == null || indicators.getBossBar() == null
                ? new Config.BossBarProgressIndicator()
                : indicators.getBossBar();
    }

    private BossBar.Color color(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Color.YELLOW;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus bossbar progress indicator color '{}'. Using YELLOW.", raw);
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay overlay(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Invalid QuestsPlus bossbar progress indicator overlay '{}'. Using PROGRESS.", raw);
            return BossBar.Overlay.PROGRESS;
        }
    }

    private record ActiveBossBar(BossBar bossBar, ScheduledTask hideTask) {
    }
}
