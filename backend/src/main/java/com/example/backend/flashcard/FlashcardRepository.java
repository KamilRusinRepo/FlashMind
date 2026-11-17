package com.example.backend.flashcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    boolean existsByDeck_Id(Long deckId);
    Page<Flashcard> findByDeck_IdAndDeck_Owner_Id(Long deckId, Long ownerId, Pageable pageable);
    Optional<Flashcard> findByIdAndDeck_IdAndDeck_Owner_Id(Long id, Long deckId, Long ownerId);
    boolean existsByDeck_IdAndDeck_Owner_Id(Long deckId, Long ownerId);
    List<Flashcard> findByDeck_IdAndDeck_Owner_IdOrderByCreatedAtAscIdAsc(Long deckId, Long ownerId);

    @Query(value = """
        select card.* from flashcards card join decks deck on deck.id = card.deck_id
        where card.deck_id = :deckId and (deck.owner_id = :userId or (deck.is_public = true and exists
            (select 1 from deck_follows follow where follow.deck_id = deck.id and follow.user_id = :userId)))
        order by random()
        """, nativeQuery = true)
    List<Flashcard> findForFreePractice(@Param("deckId") Long deckId, @Param("userId") Long userId, Pageable pageable);

    @Query("""
        select (count(card) > 0) from Flashcard card
        left join FlashcardProgress progress on progress.flashcard.id = card.id and progress.user.id = :userId
        left join card.deck.owner owner
        where (owner.id = :userId or (card.deck.publicDeck = true and exists
            (select follow.id from DeckFollow follow where follow.deck = card.deck and follow.user.id = :userId)))
          and (progress.id is null or progress.nextReviewAt is null or progress.nextReviewAt <= :now)
        """)
    boolean hasCardsForReview(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            select card from Flashcard card
            left join FlashcardProgress progress on progress.flashcard.id = card.id and progress.user.id = :userId
            left join card.deck.owner owner
            where card.deck.id = :deckId and (owner.id = :userId or (card.deck.publicDeck = true and exists
                (select follow.id from DeckFollow follow where follow.deck = card.deck and follow.user.id = :userId)))
              and (progress.id is null or progress.nextReviewAt is null or progress.nextReviewAt <= :now)
            order by case when progress.lastAnsweredAt is null then 1 else 0 end,
                     progress.nextReviewAt, card.createdAt, card.id
            """)
    List<Flashcard> findForStudy(@Param("deckId") Long deckId, @Param("userId") Long userId,
                                @Param("now") Instant now, Pageable pageable);
}
