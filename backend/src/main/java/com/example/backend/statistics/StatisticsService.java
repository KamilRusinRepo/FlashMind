package com.example.backend.statistics;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.example.backend.deck.DeckRepository;
import com.example.backend.statistics.dto.response.DeckMasteryResponse;
import com.example.backend.statistics.dto.response.MasteryReportResponse;
import com.example.backend.statistics.dto.response.StatisticsOverviewResponse;
import com.example.backend.statistics.dto.response.StatisticsOverviewResponse.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatisticsService {
    private final StatisticsRepository statistics;
    private final DeckRepository decks;
    private final Clock clock;
    private final ZoneId zone;
    public StatisticsService(StatisticsRepository statistics, DeckRepository decks, Clock clock,
                             @Value("${app.statistics.zone:Europe/Warsaw}") String zone) {
        this.statistics = statistics; this.decks = decks; this.clock = clock; this.zone = ZoneId.of(zone);
    }

    public StatisticsOverviewResponse overview(Long userId) {
        var now = clock.instant();
        var today = now.atZone(zone).toLocalDate();
        var activity = statistics.activity(userId, zone.getId(), now);
        var durations = statistics.durations(userId, zone.getId(), now);
        var mastered = statistics.mastered(userId, zone.getId(), now);
        var dates = new ArrayList<LocalDate>();
        activity.forEach(day -> dates.add(day.date()));
        durations.forEach(day -> dates.add(day.date()));
        mastered.forEach(day -> dates.add(day.date()));
        var first = dates.stream().min(LocalDate::compareTo).orElse(today);
        Map<String, LocalDate> starts = new LinkedHashMap<>();
        starts.put("day", today); starts.put("week", today.minusDays(6)); starts.put("month", today.minusDays(29)); starts.put("all", first);
        Map<String, Period> periods = new LinkedHashMap<>();
        starts.forEach((key, start) -> {
            var selected = activity.stream().filter(day -> !day.date().isBefore(start)).toList();
            long studied = selected.stream().mapToLong(DailyActivity::studied).sum();
            long correct = selected.stream().mapToLong(DailyActivity::correct).sum();
            periods.put(key, new Period(start, today,
                    mastered.stream().filter(day -> !day.date().isBefore(start)).mapToLong(StatisticsRepository.MasteredDay::count).sum(),
                    durations.stream().filter(day -> !day.date().isBefore(start)).mapToLong(StatisticsRepository.DurationDay::seconds).sum(),
                    studied, correct, StatisticsRules.accuracy(studied, correct), selected.stream().mapToLong(DailyActivity::xp).sum()));
        });
        long totalXp = periods.get("all").xp();
        var level = StatisticsRules.levelProgress(totalXp);
        var byDate = activity.stream().collect(Collectors.toMap(DailyActivity::date, Function.identity()));
        var heatmap = today.minusDays(364).datesUntil(today.plusDays(1))
                .map(day -> byDate.getOrDefault(day, new DailyActivity(day, 0, 0, 0))).toList();
        var counts = activity.stream().collect(Collectors.toMap(DailyActivity::date, DailyActivity::studied));
        return new StatisticsOverviewResponse(today, zone.getId(), new Profile(totalXp, level.level(),
                statistics.rank(userId, totalXp, now), StatisticsRules.streak(today, counts), StatisticsRules.DAILY_GOAL,
                level.currentXp(), level.requiredXp()),
                periods, heatmap, statistics.decks(userId, now).stream().limit(4).toList());
    }

    public List<DeckMasteryResponse> decks(Long userId) { return statistics.decks(userId, clock.instant()); }

    public MasteryReportResponse report(Long userId, Long deckId, int stars, String sort, int page, int size) {
        if (deckId < 1 || stars < 0 || stars > 4 || !(sort.equals("strongest") || sort.equals("weakest")) || page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var deck = decks.findByIdAndOwner_Id(deckId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var found = statistics.buckets(userId, deckId).stream().collect(Collectors.toMap(MasteryReportResponse.Bucket::level, MasteryReportResponse.Bucket::count));
        var buckets = java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(level -> new MasteryReportResponse.Bucket(level, found.getOrDefault(level, 0L))).toList();
        long total = buckets.stream().mapToLong(MasteryReportResponse.Bucket::count).sum();
        long filtered = stars == 0 ? total : found.getOrDefault(stars, 0L);
        var now = clock.instant();
        var overview = statistics.decks(userId, deckId, now).getFirst();
        return new MasteryReportResponse(deckId, deck.getName(), total, overview.masteryPercent(), now.atZone(zone).toLocalDate(), zone.getId(), buckets,
                statistics.cards(userId, deckId, stars, sort.equals("strongest"), page, size), page, size, filtered, (int) ((filtered + size - 1) / size));
    }
}
