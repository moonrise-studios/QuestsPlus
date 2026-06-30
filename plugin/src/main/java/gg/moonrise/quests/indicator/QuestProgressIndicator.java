package gg.moonrise.quests.indicator;

public interface QuestProgressIndicator {

    public String type();

    public void show(QuestProgressIndicatorContext context);

    public void clearAll();
}
