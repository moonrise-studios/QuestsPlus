package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class Daily {
    @Comment({
            "",
            "Server-local daily reset time in HH:mm format."
    })
    private String resetTime = "05:00";
    @Comment({
            "",
            "When true, personal quest windows reset weekly instead of daily."
    })
    private boolean weekly = false;
    @Comment({
            "",
            "Weekly personal quest reset schedule used only when weekly is true."
    })
    private GlobalSchedule schedule = new GlobalSchedule();
    @Comment({
            "",
            "Number of daily quest slots shown in the quest menu."
    })
    private int questCount = 3;
    @Comment({
            "",
            "Permission-based daily reroll limits."
    })
    private Rerolls rerolls = new Rerolls();

    public Daily() {
    }

    public Daily(String resetTime, boolean weekly, GlobalSchedule schedule, int questCount, Rerolls rerolls) {
        this.resetTime = resetTime;
        this.weekly = weekly;
        this.schedule = schedule;
        this.questCount = questCount;
        this.rerolls = rerolls;
    }
}
