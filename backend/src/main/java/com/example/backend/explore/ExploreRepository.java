package com.example.backend.explore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.example.backend.flashcard.dto.response.FlashcardResponse;
import com.example.backend.deck.DeckType;
import com.example.backend.deck.dto.response.DeckAuthorResponse;
import com.example.backend.explore.dto.response.PublicDeckResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExploreRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ExploreRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SELECT = """
        SELECT d.id, d.name, d.description, d.deck_type, d.category, d.source_language, d.target_language,
               d.background_url, d.created_at, author.id AS author_id, author.username AS author_name,
               (SELECT COUNT(*) FROM flashcards card WHERE card.deck_id = d.id) AS card_count,
               (SELECT COUNT(*) FROM deck_follows follow WHERE follow.deck_id = d.id) AS follower_count,
               EXISTS(SELECT 1 FROM deck_follows follow WHERE follow.deck_id = d.id AND follow.user_id = :viewerId) AS following
        """;

    // LEFT JOIN preserves public decks whose author was physically deleted.
    private static final String FROM = """
         FROM decks d LEFT JOIN users author ON author.id = d.owner_id
         WHERE d.is_public = true
           AND LOWER(CONCAT_WS(' ', d.name, d.description, COALESCE(author.username, 'Deleted user'),
                               d.category, d.source_language, d.target_language)) LIKE LOWER(:pattern) ESCAPE '!'
           AND (:deckType = '' OR d.deck_type = :deckType)
           AND (:category = '' OR LOWER(COALESCE(d.category, '')) = LOWER(:category))
           AND (:source = '' OR LOWER(d.source_language) = LOWER(:source))
           AND (:target = '' OR LOWER(COALESCE(d.target_language, '')) = LOWER(:target))
        """;

    private MapSqlParameterSource parameters(ExploreFilters filters) {
        String literal = filters.query().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new MapSqlParameterSource("pattern", "%" + literal + "%").addValue("deckType", filters.deckType())
                .addValue("category", filters.category()).addValue("source", filters.sourceLanguage()).addValue("target", filters.targetLanguage());
    }

    public long count(ExploreFilters filters) {
        return jdbc.queryForObject("SELECT COUNT(*) " + FROM, parameters(filters), Long.class);
    }

    public List<PublicDeckResponse> find(Long viewerId, ExploreFilters filters, int page, int size) {
        // SQL ordering is chosen from constants, never inserted from raw request text.
        String order = switch (filters.sort()) {
            case "cards" -> "card_count DESC, d.created_at DESC, d.id DESC";
            case "followers" -> "follower_count DESC, d.created_at DESC, d.id DESC";
            default -> "d.created_at DESC, d.id DESC";
        };
        return jdbc.query(SELECT + FROM + " ORDER BY " + order + " LIMIT :size OFFSET :offset",
                parameters(filters).addValue("viewerId", viewerId).addValue("size", size).addValue("offset", (long) page * size),
                (rs, row) -> mapDeck(rs, viewerId));
    }

    public Optional<PublicDeckResponse> findPublic(Long viewerId, Long deckId) {
        return jdbc.query(SELECT + """
            FROM decks d LEFT JOIN users author ON author.id = d.owner_id
            WHERE d.id = :deckId AND d.is_public = true
            """, new MapSqlParameterSource("deckId", deckId).addValue("viewerId", viewerId), (rs, row) -> mapDeck(rs, viewerId)).stream().findFirst();
    }

    public List<FlashcardResponse> findPublicCards(Long deckId, int page, int size) {
        return jdbc.query("""
            SELECT card.id, card.question, card.answer, card.example_sentence, card.translated_example, card.image_url, card.created_at
            FROM flashcards card JOIN decks d ON d.id = card.deck_id
            WHERE d.id = :deckId AND d.is_public = true
            ORDER BY card.created_at ASC, card.id ASC LIMIT :size OFFSET :offset
            """, new MapSqlParameterSource("deckId", deckId).addValue("size", size).addValue("offset", (long) page * size),
                (rs, row) -> new FlashcardResponse(rs.getLong("id"), rs.getString("question"), rs.getString("answer"),
                        rs.getString("example_sentence"), rs.getString("translated_example"), rs.getString("image_url"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    private PublicDeckResponse mapDeck(ResultSet rs, Long viewerId) throws SQLException {
        Long authorId = rs.getObject("author_id", Long.class);
        var author = new DeckAuthorResponse(authorId, authorId == null ? "Deleted user" : rs.getString("author_name"));
        return new PublicDeckResponse(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                DeckType.valueOf(rs.getString("deck_type")), rs.getString("category"), rs.getString("source_language"),
                rs.getString("target_language"), rs.getString("background_url"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("card_count"), author, Objects.equals(viewerId, authorId), rs.getBoolean("following"), rs.getLong("follower_count"));
    }
}
