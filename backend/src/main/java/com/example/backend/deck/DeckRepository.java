package com.example.backend.deck;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface DeckRepository extends JpaRepository<Deck, Long> {
    Optional<Deck> findByIdAndOwner_Id(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select deck from Deck deck where deck.id = :id and deck.owner.id = :ownerId")
    Optional<Deck> findOwnedForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("select deck from Deck deck where deck.owner.id = :ownerId and lower(deck.name) like lower(:pattern) escape '!'")
    Page<Deck> findOwned(@Param("ownerId") Long ownerId, @Param("pattern") String pattern, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from Deck deck where deck.owner.id = :ownerId and deck.publicDeck = false")
    void deletePrivateByOwnerId(@Param("ownerId") Long ownerId);
}
