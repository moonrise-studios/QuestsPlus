package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;

import java.time.Clock;

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
