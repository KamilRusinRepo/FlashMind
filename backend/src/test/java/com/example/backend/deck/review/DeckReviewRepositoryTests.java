package com.example.backend.deck.review;

import java.util.UUID;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DeckReviewRepositoryTests extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired DeckReviewRepository reviews;
    @Autowired JdbcTemplate jdbc;

    @Test void persistsRatingsCommentsAndTimestampsAndSupportsLookupAndPagination() {
        var owner = user(); var first = user(); var second = user();
        var deck = deck(owner);
        var review = reviews.saveAndFlush(new DeckReview(first, deck, 5, "  Great deck  "));
        var created = review.getCreatedAt();
        assertNotNull(created); assertEquals(created, review.getUpdatedAt());
        reviews.saveAndFlush(new DeckReview(second, deck, 4, null));
        var found = reviews.findByUser_IdAndDeck_Id(first.getId(), deck.getId()).orElseThrow();
        var persistedCreated = found.getCreatedAt();
        assertEquals(5, found.getRating()); assertEquals("Great deck", found.getComment());
        assertTrue(reviews.existsByUser_IdAndDeck_Id(first.getId(), deck.getId()));
        assertEquals(2, reviews.findByDeck_Id(deck.getId(), PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).getTotalElements());
        found.update(2, null); reviews.saveAndFlush(found);
        var updated = reviews.findById(review.getId()).orElseThrow();
        assertEquals(2, updated.getRating()); assertNull(updated.getComment());
        assertEquals(persistedCreated, updated.getCreatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }

    @Test void databaseRejectsDuplicatesInvalidRatingsAndOversizedOrBlankComments() {
        var owner = user(); var reviewer = user(); var another = user(); var deck = deck(owner);
        reviews.saveAndFlush(new DeckReview(reviewer, deck, 4, null));
        assertThrows(DataIntegrityViolationException.class, () -> insert(reviewer.getId(), deck.getId(), 3, null));
        for (int rating : new int[]{0, 6}) assertThrows(DataIntegrityViolationException.class, () -> insert(another.getId(), deck.getId(), rating, null));
        assertThrows(DataIntegrityViolationException.class, () -> insert(another.getId(), deck.getId(), 4, "a".repeat(1001)));
        assertThrows(DataIntegrityViolationException.class, () -> insert(another.getId(), deck.getId(), 4, " \t\n "));
        assertThrows(DataIntegrityViolationException.class, () -> insert(Long.MAX_VALUE, deck.getId(), 4, null));
        assertEquals(1, reviews.count());
    }

    @Test void deletingUserOrDeckCascadesReviewsButRemovingReviewPreservesBothParents() {
        var owner = user(); var first = user(); var second = user(); var deck = deck(owner);
        var removed = reviews.saveAndFlush(new DeckReview(first, deck, 5, null));
        var retained = reviews.saveAndFlush(new DeckReview(second, deck, 3, "Comment"));
        reviews.deleteById(removed.getId()); reviews.flush();
        assertTrue(users.existsById(first.getId())); assertTrue(decks.existsById(deck.getId()));
        reviews.saveAndFlush(new DeckReview(first, deck, 4, null));
        users.deleteById(first.getId()); users.flush();
        assertFalse(reviews.existsByUser_IdAndDeck_Id(first.getId(), deck.getId()));
        assertTrue(reviews.existsById(retained.getId()));
        decks.deleteById(deck.getId()); decks.flush();
        assertEquals(0, reviews.count()); assertTrue(users.existsById(second.getId())); assertTrue(users.existsById(owner.getId()));
    }

    private void insert(Long userId, Long deckId, int rating, String comment) {
        jdbc.update("INSERT INTO deck_reviews(user_id, deck_id, rating, comment) VALUES (?, ?, ?, ?)", userId, deckId, rating, comment);
    }
    private User user() {
        String name = "Review" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return users.saveAndFlush(new User(name, name + "@example.invalid", "test-hash"));
    }
    private Deck deck(User owner) {
        var deck = new Deck(owner, "Reviewed deck", DeckType.GENERAL, "English", null);
        deck.setPublic(true); return decks.saveAndFlush(deck);
    }
}
