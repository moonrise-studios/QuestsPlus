package gg.moonrise.quests.config.file;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;
import gg.moonrise.quests.config.indicator.ProgressIndicators;

@Getter
@Configuration
public class ProgressIndicatorsFile {
    @Comment({
            "",
            "Runtime quest progress indicators used when personal or global quest progress is applied."
    })
    private ProgressIndicators progressIndicators = new ProgressIndicators();

    public ProgressIndicatorsFile() {
    }

    public ProgressIndicatorsFile(ProgressIndicators progressIndicators) {
        this.progressIndicators = progressIndicators == null ? new ProgressIndicators() : progressIndicators;
    }

    public static ProgressIndicatorsFile fromSharedMessages(SharedMessagesFile sharedMessages) {
        if (sharedMessages == null || sharedMessages.getProgressIndicators() == null) {
            return new ProgressIndicatorsFile();
        }
        return new ProgressIndicatorsFile(sharedMessages.getProgressIndicators());
    }
}
