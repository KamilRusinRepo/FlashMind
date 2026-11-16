package com.example.backend.statistics;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.example.backend.statistics.dto.response.DeckMasteryResponse;
import com.example.backend.statistics.dto.response.MasteryReportResponse;
import com.example.backend.statistics.dto.response.StatisticsOverviewResponse.DailyActivity;
import com.example.backend.study.StudyRules;
import com.example.backend.deck.DeckType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read projections: aggregate in PostgreSQL without loading JPA entities or answer text. */
@Repository
public class StatisticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public StatisticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String XP = "CASE WHEN a.was_correct THEN CASE WHEN a.used_hint THEN :hintXp ELSE :correctXp END ELSE 0 END";

    private MapSqlParameterSource parameters(Long userId, String zone, Instant now) {
        return new MapSqlParameterSource("userId", userId).addValue("zone", zone).addValue("now", Timestamp.from(now))
                .addValue("hintXp", StudyRules.sessionXp(true, true)).addValue("correctXp", StudyRules.sessionXp(true, false));
    }

    public List<DailyActivity> activity(Long userId, String zone, Instant now) {
        return jdbc.query("""
            SELECT (a.answered_at AT TIME ZONE :zone)::date AS day,
                   COUNT(*) AS studied, COUNT(*) FILTER (WHERE a.was_correct) AS correct,
                   SUM(%s) AS xp
            FROM study_answers a JOIN study_sessions s ON s.id = a.session_id
            WHERE s.user_id = :userId AND a.answered_at <= :now
            GROUP BY day ORDER BY day
            """.formatted(XP), parameters(userId, zone, now), (rs, row) -> new DailyActivity(
                rs.getObject("day", LocalDate.class), rs.getLong("studied"), rs.getLong("correct"), rs.getLong("xp")));
    }

    public record DurationDay(LocalDate date, long seconds) {}
    public List<DurationDay> durations(Long userId, String zone, Instant now) {
        return jdbc.query("""
            SELECT (ended_at AT TIME ZONE :zone)::date AS day,
                   FLOOR(SUM(EXTRACT(EPOCH FROM (ended_at - started_at))))::bigint AS seconds
            FROM study_sessions WHERE user_id = :userId AND status = 'COMPLETED' AND ended_at <= :now
            GROUP BY day
            """, parameters(userId, zone, now), (rs, row) -> new DurationDay(rs.getObject("day", LocalDate.class), rs.getLong("seconds")));
    }

    public record MasteredDay(LocalDate date, long count) {}
    public List<MasteredDay> mastered(Long userId, String zone, Instant now) {
        return jdbc.query("""
            SELECT (p.last_answered_at AT TIME ZONE :zone)::date AS day, COUNT(*) AS total
            FROM flashcard_progress p JOIN flashcards f ON f.id = p.flashcard_id JOIN decks d ON d.id = f.deck_id
            WHERE p.user_id = :userId AND d.owner_id = :userId AND p.mastery_level = 4 AND p.last_answered_at <= :now
            GROUP BY day
            """, parameters(userId, zone, now), (rs, row) -> new MasteredDay(rs.getObject("day", LocalDate.class), rs.getLong("total")));
    }

    public long rank(Long userId, long xp, Instant now) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) + 1 FROM users u LEFT JOIN (
                SELECT s.user_id, SUM(%s) AS xp
                FROM study_answers a JOIN study_sessions s ON s.id = a.session_id
                WHERE a.answered_at <= :now GROUP BY s.user_id
            ) totals ON totals.user_id = u.id
            WHERE u.is_enabled = true AND u.id <> :userId AND COALESCE(totals.xp, 0) > :xp
            """.formatted(XP), parameters(userId, "Europe/Warsaw", now).addValue("xp", xp), Long.class);
    }

    public List<DeckMasteryResponse> decks(Long userId, Instant now) {
        return decks(userId, 0L, now);
    }

    public List<DeckMasteryResponse> decks(Long userId, Long deckId, Instant now) {
        return jdbc.query("""
            SELECT d.id, d.name, d.deck_type, COUNT(f.id) AS total,
                   COUNT(p.id) FILTER (WHERE p.mastery_level = 4) AS mastered,
                   COALESCE(ROUND(AVG(COALESCE(p.mastery_level, 0)) / 4.0 * 100), 0)::int AS mastery,
                   COALESCE(SUM(p.correct_count), 0) AS correct, COALESCE(SUM(p.wrong_count), 0) AS wrong,
                   COUNT(f.id) FILTER (WHERE p.id IS NULL OR p.next_review_at IS NULL OR p.next_review_at <= :now) AS due
            FROM decks d LEFT JOIN flashcards f ON f.deck_id = d.id
            LEFT JOIN flashcard_progress p ON p.flashcard_id = f.id AND p.user_id = :userId
            WHERE d.owner_id = :userId AND (:deckId = 0 OR d.id = :deckId) GROUP BY d.id
            ORDER BY (SELECT MAX(s.started_at) FROM study_sessions s WHERE s.deck_id = d.id AND s.user_id = :userId) DESC NULLS LAST,
                     d.created_at DESC, d.id DESC
            """, new MapSqlParameterSource("userId", userId).addValue("deckId", deckId).addValue("now", Timestamp.from(now)),
                (rs, row) -> new DeckMasteryResponse(rs.getLong("id"), rs.getString("name"), rs.getLong("total"), rs.getLong("mastered"),
                    rs.getInt("mastery"), rs.getLong("correct"), rs.getLong("wrong"), DeckType.valueOf(rs.getString("deck_type")), rs.getLong("due")));
    }

    public List<MasteryReportResponse.Bucket> buckets(Long userId, Long deckId) {
        return jdbc.query("""
            SELECT GREATEST(1, COALESCE(p.mastery_level, 0)) AS level, COUNT(*) AS total
            FROM flashcards f JOIN decks d ON d.id = f.deck_id
            LEFT JOIN flashcard_progress p ON p.flashcard_id = f.id AND p.user_id = :userId
            WHERE d.id = :deckId AND d.owner_id = :userId GROUP BY level
            """, Map.of("userId", userId, "deckId", deckId), (rs, row) -> new MasteryReportResponse.Bucket(rs.getInt("level"), rs.getLong("total")));
    }

    public List<MasteryReportResponse.Card> cards(Long userId, Long deckId, int stars, boolean strongest, int page, int size) {
        // Direction comes only from a validated enum-like choice, never raw request SQL.
        String direction = strongest ? "DESC" : "ASC";
        return jdbc.query("""
            SELECT f.id, f.question, f.answer, COALESCE(p.mastery_level, 0) AS level, p.last_answered_at, p.next_review_at
            FROM flashcards f JOIN decks d ON d.id = f.deck_id
            LEFT JOIN flashcard_progress p ON p.flashcard_id = f.id AND p.user_id = :userId
            WHERE d.id = :deckId AND d.owner_id = :userId
              AND (:stars = 0 OR GREATEST(1, COALESCE(p.mastery_level, 0)) = :stars)
            ORDER BY GREATEST(1, COALESCE(p.mastery_level, 0)) %s, LOWER(f.question), f.id
            LIMIT :size OFFSET :offset
            """.formatted(direction), new MapSqlParameterSource("userId", userId).addValue("deckId", deckId)
                .addValue("stars", stars).addValue("size", size).addValue("offset", (long) page * size), (rs, row) -> {
                    var last = rs.getTimestamp("last_answered_at");
                    var next = rs.getTimestamp("next_review_at");
                    return new MasteryReportResponse.Card(rs.getLong("id"), rs.getString("question"), rs.getString("answer"), rs.getInt("level"),
                            last == null ? null : last.toInstant(), next == null ? null : next.toInstant());
                });
    }
}
