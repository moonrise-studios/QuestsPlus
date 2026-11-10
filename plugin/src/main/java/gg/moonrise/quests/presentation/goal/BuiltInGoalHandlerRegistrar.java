package gg.moonrise.quests.presentation.goal;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.sdk.GoalHandler;
import gg.moonrise.quests.application.quest.QuestDefinitionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Hands the built-in goal handler beans to {@link QuestDefinitionService}.
 *
 * <p>The handlers depend on {@link QuestProgressService} (which depends on the definition service), so the
 * definition service cannot take them as a constructor dependency without a cycle. This component
 * depends on both sides and connects them once the context has created them.
 */
@SpringComponent
@RequiredArgsConstructor
public class BuiltInGoalHandlerRegistrar {

    private final QuestDefinitionService definitionService;
    private final List<GoalHandler> goalHandlers;

    @PostConstruct
    public void register() {
        definitionService.registerBuiltInGoalHandlers(goalHandlers);
    }
}
