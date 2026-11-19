package com.example.backend.deck.review;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckReviewRepository extends JpaRepository<DeckReview, Long> {
    Optional<DeckReview> findByUser_IdAndDeck_Id(Long userId, Long deckId);
    boolean existsByUser_IdAndDeck_Id(Long userId, Long deckId);
    Page<DeckReview> findByDeck_Id(Long deckId, Pageable pageable);
}
