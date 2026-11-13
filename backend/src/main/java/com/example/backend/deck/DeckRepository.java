package com.example.backend.deck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from Deck deck where deck.owner.id = :ownerId and deck.publicDeck = false")
    void deletePrivateByOwnerId(@Param("ownerId") Long ownerId);
}
