package gg.moonrise.quests.config.message;

import lombok.Getter;
import lombok.experimental.Delegate;
import gg.moonrise.quests.config.indicator.ProgressIndicators;

/**
 * Read-only view over every player and admin message, composed from the per-feature message
 * sections (messages.yml, daily.yml, quest-milestones.yml and streaks.yml). Getters delegate to
 * those sections, so a new message only needs to be declared once in its file class.
 */
public class Messages {
    @Delegate
    private final SharedMessages shared;
    @Delegate
    private final DailyMessages daily;
    @Delegate
    private final MilestoneMessages milestone;
    @Delegate
    private final StreakMessages streak;
    @Getter
    private final ProgressIndicators progressIndicators;

    public Messages() {
        this(null, null, null, null, null);
    }

    public Messages(
            SharedMessages shared,
            DailyMessages daily,
            MilestoneMessages milestone,
            StreakMessages streak,
            ProgressIndicators progressIndicators
    ) {
        this.shared = shared == null ? new SharedMessages() : shared;
        this.daily = daily == null ? new DailyMessages() : daily;
        this.milestone = milestone == null ? new MilestoneMessages() : milestone;
        this.streak = streak == null ? new StreakMessages() : streak;
        this.progressIndicators = progressIndicators == null ? new ProgressIndicators() : progressIndicators;
    }
}
