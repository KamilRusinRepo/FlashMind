package com.example.backend.study;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyAnswerRepository extends JpaRepository<StudyAnswer, Long> {
    List<StudyAnswer> findBySession_IdOrderByPositionAsc(Long sessionId);
}
