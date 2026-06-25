package gg.moonrise.quests.core.service;

import gg.moonrise.moss.spring.SpringComponent;
import gg.moonrise.quests.sdk.QuestVariableSelector;

import java.util.List;
import java.util.random.RandomGenerator;

@SpringComponent
public class ListQuestVariableSelector implements QuestVariableSelector {

    @Override
    public String type() {
        return "LIST";
    }

    @Override
    public String select(List<String> values, RandomGenerator random) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("LIST selector requires at least one value");
        }
        return values.get(random.nextInt(values.size()));
    }
}
