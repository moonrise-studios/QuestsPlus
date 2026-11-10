package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import gg.moonrise.quests.config.menu.QuestMenu;
import gg.moonrise.quests.config.menu.QuestResetButton;
import gg.moonrise.quests.config.menu.QuestResetMenu;

@Getter
@Configuration
public class QuestResetsFile {
    @Comment({
            "",
            "Button shown in the daily quest menu for completed-quest reset purchases."
    })
    private QuestResetButton button = new QuestResetButton();
    @Comment({
            "",
            "Purchase choice menu, per-window reset limit, and reset status text."
    })
    private QuestResetMenu menu = new QuestResetMenu();

    public QuestResetsFile() {
    }

    public QuestResetsFile(QuestResetButton button, QuestResetMenu menu) {
        this.button = button == null ? new QuestResetButton() : button;
        this.menu = menu == null ? new QuestResetMenu() : menu;
    }

    public static QuestResetsFile fromMenu(QuestMenu menu) {
        if (menu == null) {
            return new QuestResetsFile();
        }
        return new QuestResetsFile(menu.getResetButton(), menu.getResetMenu());
    }
}
