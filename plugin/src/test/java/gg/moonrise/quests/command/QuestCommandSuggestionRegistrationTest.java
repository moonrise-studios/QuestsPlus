package gg.moonrise.quests.command;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuestCommandSuggestionRegistrationTest {

    @Test
    public void everyReferencedSuggestionProviderIsDeclaredOnce() {
        List<String> declared = Arrays.stream(QuestCommand.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Suggestions.class))
                .map(method -> method.getAnnotation(Suggestions.class).value())
                .toList();
        Set<String> referenced = new TreeSet<>();
        for (Method method : QuestCommand.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                Argument argument = parameter.getAnnotation(Argument.class);
                if (argument != null && !argument.suggestions().isEmpty()) {
                    referenced.add(argument.suggestions());
                }
            }
        }

        assertEquals(declared.size(), Set.copyOf(declared).size(), "Duplicate @Suggestions names: " + declared);
        assertTrue(declared.containsAll(referenced), "Missing @Suggestions for " + referenced + " in " + declared);
    }
}
