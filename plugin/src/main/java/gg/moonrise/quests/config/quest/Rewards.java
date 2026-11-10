package gg.moonrise.quests.config.quest;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.Getter;

import java.util.List;

@Getter
@Configuration
public class Rewards {
    @Comment({
            "",
            "Console commands run for this reward set; supports quest reward placeholders."
    })
    private List<String> commands = List.of();

    public Rewards() {
    }

    public Rewards(List<String> commands) {
        this.commands = commands;
    }
}
