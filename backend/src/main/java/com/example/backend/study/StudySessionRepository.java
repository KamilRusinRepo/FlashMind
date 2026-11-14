package com.example.backend.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    Optional<StudySession> findByIdAndUser_Id(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from StudySession session where session.id = :id and session.user.id = :userId")
    Optional<StudySession> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
