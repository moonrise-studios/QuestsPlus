package gg.moonrise.quests.infrastructure.time;

import gg.moonrise.moss.spring.SpringComponent;

import java.time.Clock;
import gg.moonrise.quests.core.service.QuestClock;

/**
 * {@link QuestClock} backed by the system clock in the server's default time zone.
 */
@SpringComponent
public class SystemQuestClock implements QuestClock {

    @Override
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
