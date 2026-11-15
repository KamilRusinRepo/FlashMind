package com.example.backend.statistics;

import java.time.LocalDate;
import java.util.Map;

public final class StatisticsRules {
    public static final int DAILY_GOAL = 20;
    private StatisticsRules() {}

    public static int level(long xp) {
        return levelProgress(xp).level();
    }

    public record LevelProgress(int level, long currentXp, long requiredXp) {}

    public static LevelProgress levelProgress(long xp) {
        int level = 1;
        long previousThreshold = 0;
        long requirement = 100;
        long threshold = requirement;
        while (xp >= threshold) {
            level++;
            previousThreshold = threshold;
            requirement = (long) Math.ceil(requirement * 1.33);
            if (Long.MAX_VALUE - threshold < requirement) { threshold = Long.MAX_VALUE; break; }
            threshold += requirement;
        }
        return new LevelProgress(level, Math.max(0, xp - previousThreshold), Math.max(1, threshold - previousThreshold));
    }

    public static int streak(LocalDate today, Map<LocalDate, Long> activity) {
        LocalDate day = activity.getOrDefault(today, 0L) >= DAILY_GOAL ? today : today.minusDays(1);
        int count = 0;
        while (activity.getOrDefault(day, 0L) >= DAILY_GOAL) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    public static int accuracy(long studied, long correct) {
        return studied == 0 ? 0 : (int) Math.round(100.0 * correct / studied);
    }
}
