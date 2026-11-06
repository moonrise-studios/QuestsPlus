package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class QuestMenuFile {
    @Comment({
            "",
            "Daily quest menu, empty-slot item, active/completed quest items, and difficulty picker layout."
    })
    private QuestMenu menu = new QuestMenu();

    public QuestMenuFile() {
    }

    public QuestMenuFile(QuestMenu menu) {
        this.menu = menu == null ? new QuestMenu() : menu;
    }

    public static QuestMenuFile fromDaily(DailyFile daily) {
        if (daily == null || daily.getMenu() == null) {
            return new QuestMenuFile();
        }
        return new QuestMenuFile(daily.getMenu());
    }
}
