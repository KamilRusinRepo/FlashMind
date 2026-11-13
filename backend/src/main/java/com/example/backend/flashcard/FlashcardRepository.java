package com.example.backend.flashcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    Page<Flashcard> findByDeck_IdAndDeck_Owner_Id(Long deckId, Long ownerId, Pageable pageable);
}
