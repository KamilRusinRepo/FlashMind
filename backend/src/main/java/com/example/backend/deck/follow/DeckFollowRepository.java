package com.example.backend.deck.follow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckFollowRepository extends JpaRepository<DeckFollow, Long> {
    boolean existsByUser_IdAndDeck_Id(Long userId, Long deckId);
    void deleteByUser_IdAndDeck_Id(Long userId, Long deckId);
}
