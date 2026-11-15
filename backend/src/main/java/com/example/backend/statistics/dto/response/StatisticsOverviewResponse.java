package com.example.backend.statistics.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StatisticsOverviewResponse(LocalDate today, String zone, Profile profile,
        Map<String, Period> periods, List<DailyActivity> activity, List<DeckMasteryResponse> decks) {
    public record Profile(long totalXp, int level, long globalRank, int streak, int dailyGoal) {}
    public record Period(LocalDate start, LocalDate end, long mastered, long studySeconds,
                         long studied, long correct, int accuracy, long xp) {}
    public record DailyActivity(LocalDate date, long studied, long correct, long xp) {}
}
