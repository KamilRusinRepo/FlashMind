package com.example.backend.flashcard;

import com.example.backend.deck.Deck;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.DeckType;
import com.example.backend.support.PostgresIntegrationTest;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FlashcardRepositoryTests extends PostgresIntegrationTest {
    @Autowired FlashcardRepository flashcards;
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void persistsFlashcardWithDeckAndOptionalImage() {
        var owner = users.saveAndFlush(new User("CardOwner", "card-owner@example.invalid", "test-hash"));
        var deck = decks.saveAndFlush(new Deck(owner, "Vocabulary", DeckType.LANGUAGE, "English", "Polish"));
        var card = new Flashcard(deck, "  curiosity  ", "  ciekawość  ");
        card.setExampleSentence("  Curiosity helps us learn.  ");
        card.setTranslatedExample("  Ciekawość pomaga nam się uczyć.  ");
        card.setImageUrl("  https://example.invalid/curiosity.jpg  ");
        Long id = flashcards.saveAndFlush(card).getId();
        entityManager.clear();

        var loaded = flashcards.findById(id).orElseThrow();
        assertEquals(deck.getId(), loaded.getDeck().getId());
        assertEquals("curiosity", loaded.getQuestion());
        assertEquals("ciekawość", loaded.getAnswer());
        assertEquals("Curiosity helps us learn.", loaded.getExampleSentence());
        assertEquals("Ciekawość pomaga nam się uczyć.", loaded.getTranslatedExample());
        assertEquals("https://example.invalid/curiosity.jpg", loaded.getImageUrl());
        assertNotNull(loaded.getCreatedAt());

        loaded.setImageUrl(" ");
        flashcards.saveAndFlush(loaded);
        entityManager.clear();
        assertNull(flashcards.findById(id).orElseThrow().getImageUrl());
    }

    @Test
    void deletingDeckCascadesOnlyItsFlashcards() {
        var owner = users.saveAndFlush(new User("CascadeOwner", "cascade-owner@example.invalid", "test-hash"));
        var firstDeck = decks.saveAndFlush(new Deck(owner, "First deck", DeckType.GENERAL, "English", null));
        var otherDeck = decks.saveAndFlush(new Deck(owner, "Other deck", DeckType.GENERAL, "English", null));
        Long firstId = flashcards.saveAndFlush(new Flashcard(firstDeck, "First question", "First answer")).getId();
        Long otherId = flashcards.saveAndFlush(new Flashcard(otherDeck, "Other question", "Other answer")).getId();

        // A DB cascade does not update other entities already managed by Hibernate.
        // Start deletion with a fresh persistence context, like a separate HTTP request.
        entityManager.flush();
        entityManager.clear();
        decks.deleteById(firstDeck.getId());
        decks.flush();
        entityManager.clear();

        assertFalse(flashcards.existsById(firstId));
        assertTrue(flashcards.existsById(otherId));
        assertTrue(decks.existsById(otherDeck.getId()));
        assertTrue(users.existsById(owner.getId()));
    }
}
