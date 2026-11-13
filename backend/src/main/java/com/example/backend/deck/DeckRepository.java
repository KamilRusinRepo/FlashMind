package com.example.backend.deck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    @Query("select deck from Deck deck where deck.owner.id = :ownerId and lower(deck.name) like lower(:pattern) escape '!'")
    Page<Deck> findOwned(@Param("ownerId") Long ownerId, @Param("pattern") String pattern, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from Deck deck where deck.owner.id = :ownerId and deck.publicDeck = false")
    void deletePrivateByOwnerId(@Param("ownerId") Long ownerId);
}
