package gg.moonrise.quests.ui;

import gg.moonrise.quests.model.QuestDifficulty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

final class DifficultySelectorSlots {

    private DifficultySelectorSlots() {
    }

    static List<Integer> sanitizeSlots(List<Integer> configuredSlots, int totalSlots) {
        List<Integer> source = configuredSlots == null ? List.of() : configuredSlots;
        List<Integer> sanitized = new ArrayList<>(source.stream()
                .filter(Objects::nonNull)
                .filter(slot -> slot >= 0 && slot < totalSlots)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll));

        if (!sanitized.isEmpty()) {
            return sanitized;
        }

        return IntStream.range(0, totalSlots)
                .boxed()
                .toList();
    }

    static Map<QuestDifficulty, Integer> resolve(
            List<QuestDifficulty> difficulties,
            ToIntFunction<QuestDifficulty> configuredSlot,
            List<Integer> fallbackSlots,
            int totalSlots,
            Set<Integer> reservedSlots
    ) {
        List<Integer> fallbacks = sanitizeSlots(fallbackSlots, totalSlots);
        Set<Integer> occupiedSlots = new HashSet<>(reservedSlots == null ? Set.of() : reservedSlots);
        Map<QuestDifficulty, Integer> resolved = new LinkedHashMap<>();

        for (QuestDifficulty difficulty : difficulties) {
            int slot = configuredSlot.applyAsInt(difficulty);
            if (isAvailable(slot, totalSlots, occupiedSlots)) {
                resolved.put(difficulty, slot);
                occupiedSlots.add(slot);
                continue;
            }

            Integer fallback = nextFallback(fallbacks, totalSlots, occupiedSlots);
            if (fallback != null) {
                resolved.put(difficulty, fallback);
                occupiedSlots.add(fallback);
            }
        }

        return resolved;
    }

    private static boolean isAvailable(int slot, int totalSlots, Set<Integer> occupiedSlots) {
        return slot >= 0 && slot < totalSlots && !occupiedSlots.contains(slot);
    }

    private static Integer nextFallback(List<Integer> fallbackSlots, int totalSlots, Set<Integer> occupiedSlots) {
        for (Integer fallbackSlot : fallbackSlots) {
            if (fallbackSlot != null && isAvailable(fallbackSlot, totalSlots, occupiedSlots)) {
                return fallbackSlot;
            }
        }
        return null;
    }
}
