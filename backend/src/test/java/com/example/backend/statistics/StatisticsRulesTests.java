package com.example.backend.statistics;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsRulesTests {
    @Test void levelsFollowIncreasingThresholdsFromTheOriginalApplication() {
        assertEquals(1, StatisticsRules.level(0));
        assertEquals(1, StatisticsRules.level(99));
        assertEquals(2, StatisticsRules.level(100));
        assertEquals(2, StatisticsRules.level(232));
        assertEquals(3, StatisticsRules.level(233));
        assertEquals(4, StatisticsRules.level(410));
        assertEquals(new StatisticsRules.LevelProgress(1, 0, 100), StatisticsRules.levelProgress(0));
        assertEquals(new StatisticsRules.LevelProgress(1, 99, 100), StatisticsRules.levelProgress(99));
        assertEquals(new StatisticsRules.LevelProgress(2, 0, 133), StatisticsRules.levelProgress(100));
        assertEquals(new StatisticsRules.LevelProgress(2, 132, 133), StatisticsRules.levelProgress(232));
        assertEquals(new StatisticsRules.LevelProgress(3, 0, 177), StatisticsRules.levelProgress(233));
    }
    @Test void unfinishedTodayKeepsYesterdaysStreakButMissingYesterdayBreaksIt() {
        var today = LocalDate.of(2026, 11, 16);
        assertEquals(2, StatisticsRules.streak(today, Map.of(today, 19L, today.minusDays(1), 20L, today.minusDays(2), 30L)));
        assertEquals(3, StatisticsRules.streak(today, Map.of(today, 20L, today.minusDays(1), 20L, today.minusDays(2), 30L)));
        assertEquals(0, StatisticsRules.streak(today, Map.of(today.minusDays(2), 50L)));
    }
    @Test void accuracyUsesAllAttemptsAndHandlesAnEmptyPeriod() {
        assertEquals(0, StatisticsRules.accuracy(0, 0));
        assertEquals(67, StatisticsRules.accuracy(3, 2));
        assertEquals(50, StatisticsRules.accuracy(100, 50));
    }
}
