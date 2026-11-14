package com.example.backend.study;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyAnswerRepository extends JpaRepository<StudyAnswer, Long> {
    List<StudyAnswer> findBySession_IdOrderByPositionAsc(Long sessionId);
    Optional<StudyAnswer> findByIdAndSession_Id(Long id, Long sessionId);
    Optional<StudyAnswer> findFirstBySession_IdAndAnsweredAtIsNullOrderByPositionAsc(Long sessionId);
    boolean existsBySession_User_IdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(Long userId, java.time.Instant start, java.time.Instant end);
}
