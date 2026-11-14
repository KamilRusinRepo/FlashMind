package com.example.backend.study;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.flashcard.Flashcard;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserDeletionService;
import com.example.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class StudyRepositoryTests extends PostgresIntegrationTest {
    private static final Instant START = Instant.parse("2026-11-14T12:00:00Z");
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAnswerRepository answers;
    @Autowired FlashcardProgressRepository progress;
    @Autowired DeckRepository decks;
    @Autowired FlashcardRepository cards;
    @Autowired UserRepository users;
    @Autowired UserDeletionService userDeletion;
    @Autowired EntityManager entityManager;

    @Test
    void persistsSessionOrderedAnswersOriginalTextAndProgressWithoutChangingSnapshots() {
        var user = account("Roundtrip");
        var deck = deck(user);
        var first = card(deck);
        first.setExampleSentence("An example");
        first.setTranslatedExample("Przykład");
        first.setImageUrl("https://example.invalid/card.jpg");
        cards.saveAndFlush(first);
        var second = card(deck);
        var session = sessions.saveAndFlush(new StudySession(user, deck, 2, START));
        var answer = answers.saveAndFlush(new StudyAnswer(session, first, 1));
        var skipped = answers.saveAndFlush(new StudyAnswer(session, second, 2));
        assertFalse(answer.isAnswered());
        assertNull(answer.getCorrect());
        assertNull(answer.getUserAnswer());

        first.setQuestion("Edited question");
        first.setAnswer("Edited answer");
        cards.saveAndFlush(first);
        answer.markHintUsed();
        answer.recordAnswer("  CIEKAWOŚĆ  ", true, false, START.plusSeconds(5));
        assertThrows(IllegalStateException.class, () -> answer.recordAnswer("Changed", false, false, START.plusSeconds(6)));
        skipped.recordAnswer("", false, true, START.plusSeconds(10));
        session.complete(START.plusSeconds(20));
        assertThrows(IllegalStateException.class, () -> session.abandon(START.plusSeconds(21)));
        var state = new FlashcardProgress(user, first);
        state.recordReview(true, 2, 0, START.plusSeconds(5), START.plus(3, ChronoUnit.DAYS));
        progress.saveAndFlush(state);
        entityManager.flush();
        entityManager.clear();

        var loaded = sessions.findByIdAndUser_Id(session.getId(), user.getId()).orElseThrow();
        assertEquals(StudySessionStatus.COMPLETED, loaded.getStatus());
        assertEquals(2, loaded.getTotalCards());
        assertEquals(START.plusSeconds(20), loaded.getEndedAt());
        var rows = answers.findBySession_IdOrderByPositionAsc(session.getId());
        assertEquals(2, rows.size());
        var saved = rows.getFirst();
        assertEquals(1, saved.getPosition());
        assertEquals("curiosity", saved.getQuestionSnapshot());
        assertEquals("ciekawość", saved.getAnswerSnapshot());
        assertEquals("An example", saved.getExampleSentenceSnapshot());
        assertEquals("Przykład", saved.getTranslatedExampleSnapshot());
        assertEquals("https://example.invalid/card.jpg", saved.getImageUrlSnapshot());
        assertEquals("  CIEKAWOŚĆ  ", saved.getUserAnswer());
        assertTrue(saved.getCorrect());
        assertTrue(saved.isUsedHint());
        assertTrue(rows.getLast().isSkipped());
        assertFalse(rows.getLast().getCorrect());
        var savedProgress = progress.findByUser_IdAndFlashcard_Id(user.getId(), first.getId()).orElseThrow();
        assertEquals(1, savedProgress.getAttempts());
        assertEquals(1, savedProgress.getCorrectCount());
        assertEquals(0, savedProgress.getWrongCount());
        assertEquals(2, savedProgress.getMasteryLevel());
        assertEquals(START.plus(3, ChronoUnit.DAYS), savedProgress.getNextReviewAt());
    }

    @Test
    void deletingFlashcardPreservesSessionAnswerAndRemovesItsProgress() {
        var user = account("CardDeletion");
        var deck = deck(user);
        var card = card(deck);
        var session = sessions.saveAndFlush(new StudySession(user, deck, 1, START));
        var answer = new StudyAnswer(session, card, 1);
        answer.recordAnswer("ciekawość", true, false, START.plusSeconds(5));
        Long answerId = answers.saveAndFlush(answer).getId();
        progress.saveAndFlush(new FlashcardProgress(user, card));

        cards.delete(card);
        cards.flush();
        entityManager.clear();

        var retained = answers.findById(answerId).orElseThrow();
        assertNull(retained.getFlashcard());
        assertEquals("curiosity", retained.getQuestionSnapshot());
        assertEquals("ciekawość", retained.getUserAnswer());
        assertTrue(sessions.existsById(session.getId()));
        assertTrue(progress.findByUser_IdAndFlashcard_Id(user.getId(), card.getId()).isEmpty());
    }

    @Test
    void deletingAccountRemovesOwnLearningDataButPreservesPublicDeckAndOtherLearners() {
        var owner = account("DeletedOwner");
        var learner = account("RetainedLearner");
        var privateDeck = deck(owner);
        var privateCard = card(privateDeck);
        var publicDeck = deck(owner);
        publicDeck.setPublic(true);
        decks.saveAndFlush(publicDeck);
        var publicCard = card(publicDeck);
        var privateSession = sessions.saveAndFlush(new StudySession(owner, privateDeck, 1, START));
        Long privateAnswerId = answers.saveAndFlush(new StudyAnswer(privateSession, privateCard, 1)).getId();
        var ownerSession = sessions.saveAndFlush(new StudySession(owner, publicDeck, 1, START));
        Long ownerAnswerId = answers.saveAndFlush(new StudyAnswer(ownerSession, publicCard, 1)).getId();
        var learnerSession = sessions.saveAndFlush(new StudySession(learner, publicDeck, 1, START));
        Long learnerAnswerId = answers.saveAndFlush(new StudyAnswer(learnerSession, publicCard, 1)).getId();
        Long ownerProgressId = progress.saveAndFlush(new FlashcardProgress(owner, publicCard)).getId();
        Long learnerProgressId = progress.saveAndFlush(new FlashcardProgress(learner, publicCard)).getId();

        userDeletion.deleteAccount(owner.getId());
        entityManager.clear();

        assertFalse(users.existsById(owner.getId()));
        assertFalse(decks.existsById(privateDeck.getId()));
        assertFalse(sessions.existsById(privateSession.getId()));
        assertFalse(sessions.existsById(ownerSession.getId()));
        assertFalse(answers.existsById(privateAnswerId));
        assertFalse(answers.existsById(ownerAnswerId));
        assertFalse(progress.existsById(ownerProgressId));
        assertNull(decks.findById(publicDeck.getId()).orElseThrow().getOwner());
        assertTrue(cards.existsById(publicCard.getId()));
        assertTrue(answers.existsById(learnerAnswerId));
        assertTrue(progress.existsById(learnerProgressId));
    }

    @Test
    void versionRejectsAStaleConcurrentProgressUpdate() {
        var user = account("ConcurrentProgress");
        var card = card(deck(user));
        Long id = progress.saveAndFlush(new FlashcardProgress(user, card)).getId();
        entityManager.clear();
        var stale = progress.findById(id).orElseThrow();
        entityManager.detach(stale);
        var current = progress.findById(id).orElseThrow();
        current.recordReview(true, 2, 0, START, START.plus(1, ChronoUnit.DAYS));
        progress.saveAndFlush(current);
        entityManager.clear();
        stale.recordReview(false, 1, 1, START, START.plus(1, ChronoUnit.DAYS));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> progress.saveAndFlush(stale));
    }

    private User account(String name) { return users.saveAndFlush(new User(name, name.toLowerCase() + "@example.invalid", "test-hash")); }
    private Deck deck(User user) { return decks.saveAndFlush(new Deck(user, "Deck", DeckType.LANGUAGE, "English", "Polish")); }
    private Flashcard card(Deck deck) { return cards.saveAndFlush(new Flashcard(deck, "curiosity", "ciekawość")); }
}
