package com.example.backend.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardProgressRepository extends JpaRepository<FlashcardProgress, Long> {
    Optional<FlashcardProgress> findByUser_IdAndFlashcard_Id(Long userId, Long flashcardId);
}
