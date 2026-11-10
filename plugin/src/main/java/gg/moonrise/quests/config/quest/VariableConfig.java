package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class VariableConfig {
    @Comment({
            "",
            "Variable selector type. Currently supported: LIST."
    })
    private String selector = "LIST";
    @Comment({
            "",
            "Values available to the selector when a quest is generated."
    })
    private List<String> values = List.of();

    public VariableConfig() {
    }

    public VariableConfig(String selector, List<String> values) {
        this.selector = selector;
        this.values = values;
    }
}
