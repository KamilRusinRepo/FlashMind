package com.example.backend.deck.review;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckReviewRepository extends JpaRepository<DeckReview, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "deck"})
    Page<DeckReview> findAll(Pageable pageable);

    @Query("select review.deck.id from DeckReview review where review.id = :reviewId")
    Optional<Long> findDeckId(@Param("reviewId") Long reviewId);

    @EntityGraph(attributePaths = "user")
    Optional<DeckReview> findByUser_IdAndDeck_Id(Long userId, Long deckId);
    boolean existsByUser_IdAndDeck_Id(Long userId, Long deckId);
    @EntityGraph(attributePaths = "user")
    Page<DeckReview> findByDeck_Id(Long deckId, Pageable pageable);

    @Query("select review.rating as rating, count(review) as total from DeckReview review where review.deck.id = :deckId group by review.rating")
    List<RatingCount> countRatings(@Param("deckId") Long deckId);

    interface RatingCount {
        int getRating();
        long getTotal();
    }
}
