package gg.moonrise.quests.indicator;

public interface QuestProgressIndicator {

    String type();

    void show(QuestProgressIndicatorContext context);

    void clearAll();
}
