package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.Ignore;
import lombok.Getter;
import gg.moonrise.quests.config.indicator.ProgressIndicators;
import gg.moonrise.quests.config.menu.MilestoneMenu;
import gg.moonrise.quests.config.message.MilestoneMessages;
import gg.moonrise.quests.config.message.SharedMessages;

@Getter
@Configuration
public class SharedMessagesFile {
    @Comment({
            "",
            "Shared command and admin messages that are not tied to one feature file."
    })
    private SharedMessages messages = new SharedMessages();
    @Comment({
            "",
            "Messages used for completed quest totals and per-difficulty summaries."
    })
    @Ignore
    private MilestoneMessages milestoneMessages = new MilestoneMessages();
    @Comment({
            "",
            "Quest milestone selector and milestone page menu layout."
    })
    @Ignore
    private MilestoneMenu milestoneMenu = new MilestoneMenu();
    @Comment({
            "",
            "Progress indicator UI settings used when personal or global quest progress is applied."
    })
    @Ignore
    private ProgressIndicators progressIndicators = new ProgressIndicators();

    public SharedMessagesFile() {
    }

    public SharedMessagesFile(MilestoneMessages milestoneMessages) {
        this.milestoneMessages = milestoneMessages == null ? new MilestoneMessages() : milestoneMessages;
    }
}
