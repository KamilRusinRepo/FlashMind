package com.example.backend.statistics;

import java.time.LocalDate;
import java.util.Map;

public final class StatisticsRules {
    public static final int DAILY_GOAL = 20;
    private StatisticsRules() {}

    public static int level(long xp) {
        int level = 1;
        long requirement = 100;
        long threshold = requirement;
        while (xp >= threshold) {
            level++;
            requirement = (long) Math.ceil(requirement * 1.33);
            if (Long.MAX_VALUE - threshold < requirement) break;
            threshold += requirement;
        }
        return level;
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
