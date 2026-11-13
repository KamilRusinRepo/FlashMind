package com.example.backend.deck;

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
class DeckRepositoryTests extends PostgresIntegrationTest {
    @Autowired DeckRepository decks;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void persistsLanguageDeckWithOwnerAndPrivateDefault() {
        var owner = users.saveAndFlush(new User("DeckOwner", "deck-owner@example.invalid", "test-hash"));
        var deck = new Deck(owner, "  English A1  ", DeckType.LANGUAGE, "English", "Polish");
        deck.setDescription("Everyday vocabulary");
        deck.setCategory("Language");
        deck.setBackgroundUrl("https://example.invalid/cover.png");
        Long id = decks.saveAndFlush(deck).getId();
        entityManager.clear();

        var loaded = decks.findById(id).orElseThrow();
        assertEquals(owner.getId(), loaded.getOwner().getId());
        assertEquals("English A1", loaded.getName());
        assertEquals(DeckType.LANGUAGE, loaded.getDeckType());
        assertEquals("English", loaded.getSourceLanguage());
        assertEquals("Polish", loaded.getTargetLanguage());
        assertEquals("Everyday vocabulary", loaded.getDescription());
        assertEquals("Language", loaded.getCategory());
        assertEquals("https://example.invalid/cover.png", loaded.getBackgroundUrl());
        assertFalse(loaded.isPublic());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void deletingDeckPreservesItsOwnerAndOtherDecks() {
        var owner = users.saveAndFlush(new User("FirstOwner", "first-owner@example.invalid", "test-hash"));
        var other = users.saveAndFlush(new User("OtherOwner", "other-owner@example.invalid", "test-hash"));
        var first = decks.saveAndFlush(new Deck(owner, "First deck", DeckType.GENERAL, "English", null));
        decks.delete(first);
        decks.flush();
        assertTrue(users.existsById(owner.getId()));

        Long otherId = decks.saveAndFlush(new Deck(other, "Other deck", DeckType.GENERAL, "English", null)).getId();
        entityManager.clear();
        assertTrue(decks.existsById(otherId));
        assertTrue(users.existsById(other.getId()));
    }

    @Test
    void databaseClearsOwnerWhenAccountIsPhysicallyDeleted() {
        var owner = users.saveAndFlush(new User("RetainedOwner", "retained@example.invalid", "test-hash"));
        var deck = new Deck(owner, "Retained deck", DeckType.GENERAL, "English", null);
        deck.setPublic(true);
        Long id = decks.saveAndFlush(deck).getId();
        users.delete(owner);
        users.flush();
        entityManager.clear();
        var retained = decks.findById(id).orElseThrow();
        assertNull(retained.getOwner());
        assertTrue(retained.isPublic());
    }
}
