package gg.moonrise.quests.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

@Getter
@Configuration
public class QuestMilestonesFile {
    @Comment({
            "",
            "Whether quest completion milestones, milestone rewards, and milestone menus are enabled."
    })
    private boolean enabled = true;
    @Comment({
            "",
            "Messages used when players view completed totals and receive completion milestone rewards."
    })
    private MilestoneMessages messages = new MilestoneMessages();
    @Comment({
            "",
            "Quest milestone selector and milestone page menu layout."
    })
    private MilestoneMenu menu = new MilestoneMenu();

    public QuestMilestonesFile() {
    }

    public QuestMilestonesFile(MilestoneMessages messages, MilestoneMenu menu) {
        this.enabled = true;
        this.messages = messages == null ? new MilestoneMessages() : messages;
        this.menu = menu == null ? new MilestoneMenu() : menu;
    }

    public static QuestMilestonesFile fromSharedMessages(SharedMessagesFile sharedMessages) {
        if (sharedMessages == null) {
            return new QuestMilestonesFile();
        }
        return new QuestMilestonesFile(sharedMessages.getMilestoneMessages(), sharedMessages.getMilestoneMenu());
    }
}
