package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Ignore;
import lombok.Getter;
import gg.moonrise.quests.config.feature.Daily;
import gg.moonrise.quests.config.feature.Rerolls;
import gg.moonrise.quests.config.menu.QuestMenu;
import gg.moonrise.quests.config.message.DailyMessages;
import gg.moonrise.quests.config.quest.GlobalSchedule;

@Getter
@Configuration
public class DailyFile {
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
    @Comment({
            "",
            "Daily quest menu, empty-slot item, quest item, and difficulty picker layout."
    })
    @Ignore
    private QuestMenu menu = new QuestMenu();
    @Comment({
            "",
            "Daily quest, progress, reset, and reroll messages."
    })
    private DailyMessages messages = new DailyMessages();

    public DailyFile() {
    }

    public DailyFile(int questCount) {
        this.questCount = questCount;
    }

    public DailyFile(int questCount, QuestMenu menu) {
        this.questCount = questCount;
        this.menu = menu == null ? new QuestMenu() : menu;
    }

    public Daily toDaily() {
        return new Daily(
                resetTime,
                weekly,
                schedule == null ? new GlobalSchedule() : schedule,
                questCount,
                rerolls == null ? new Rerolls() : rerolls
        );
    }
}
