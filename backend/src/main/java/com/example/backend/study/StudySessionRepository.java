package com.example.backend.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    Optional<StudySession> findByIdAndUser_Id(Long id, Long userId);
}
