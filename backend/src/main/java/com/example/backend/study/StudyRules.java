package com.example.backend.study;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class StudyRules {
    private StudyRules() {}

    private static List<String> options(String answer) {
        return Arrays.stream(answer.split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    public static boolean matches(String submitted, String expected) {
        var guess = submitted.strip().toLowerCase(Locale.ROOT);
        return !guess.isEmpty() && options(expected).stream().anyMatch(option -> option.toLowerCase(Locale.ROOT).equals(guess));
    }

    public static String hint(String answer) {
        var first = options(answer).stream().findFirst().orElse("");
        int length = first.codePointCount(0, first.length());
        return first.substring(0, first.offsetByCodePoints(0, (length + 2) / 3));
    }

    public record ReviewPlan(int masteryLevel, int wrongStreak, int intervalDays) {}

    public static ReviewPlan schedule(boolean correct, int mastery, int wrongStreak) {
        if (correct) {
            int level = mastery == 0 ? 2 : Math.min(4, mastery + 1);
            return new ReviewPlan(level, 0, switch (level) { case 1 -> 1; case 2 -> 3; case 3 -> 7; default -> 14; });
        }
        if (mastery == 0) return new ReviewPlan(1, 1, 1);
        return wrongStreak >= 1 ? new ReviewPlan(Math.max(1, mastery - 1), 0, 1) : new ReviewPlan(mastery, 1, 1);
    }
}
