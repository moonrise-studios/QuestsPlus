package gg.moonrise.quests.entity;

public record QuestResetEligibility(int completed, int required) {

    public boolean eligible() {
        return required > 0 && completed >= required;
    }
}
