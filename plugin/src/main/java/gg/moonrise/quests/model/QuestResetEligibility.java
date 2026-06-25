package gg.moonrise.quests.model;

public record QuestResetEligibility(int completed, int required) {

    public boolean eligible() {
        return required > 0 && completed >= required;
    }
}
