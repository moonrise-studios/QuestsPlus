package gg.moonrise.quests.core.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Time source for quest resets and global quest periods. Reset windows are computed in the clock's
 * zone, so daylight saving transitions move wall-clock boundaries instead of skewing scheduled delays.
 */
public interface QuestClock {

    Clock clock();

    default ZonedDateTime now() {
        return ZonedDateTime.now(clock());
    }

    default Instant instant() {
        return clock().instant();
    }
}
