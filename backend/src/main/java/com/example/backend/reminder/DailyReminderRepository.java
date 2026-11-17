package com.example.backend.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyReminderRepository extends JpaRepository<DailyReminderDispatch, Long> {
    boolean existsByUser_IdAndReminderDate(Long userId, LocalDate date);
    @Query("select dispatch.user.id from DailyReminderDispatch dispatch where dispatch.id = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dispatch from DailyReminderDispatch dispatch where dispatch.id = :id")
    Optional<DailyReminderDispatch> findForUpdate(@Param("id") Long id);
    @Query("select dispatch.id from DailyReminderDispatch dispatch where dispatch.status = :status order by dispatch.id")
    List<Long> findPendingIds(@Param("status") DailyReminderDispatch.Status status, Pageable pageable);

    @Query("""
        select user.id from User user
        where user.dailyRemindersEnabled = true and user.enabled = true and user.emailVerifiedAt is not null
          and not exists (select dispatch.id from DailyReminderDispatch dispatch where dispatch.user.id = user.id and dispatch.reminderDate = :date)
          and not exists (select answer.id from StudyAnswer answer where answer.session.user.id = user.id and answer.answeredAt >= :start and answer.answeredAt < :end)
          and exists (select card.id from Flashcard card
              left join FlashcardProgress progress on progress.flashcard.id = card.id and progress.user.id = user.id
              left join card.deck.owner owner
              where (owner.id = user.id or (card.deck.publicDeck = true and exists
                  (select follow.id from DeckFollow follow where follow.deck = card.deck and follow.user.id = user.id)))
                and (progress.id is null or progress.nextReviewAt is null or progress.nextReviewAt <= :now))
        order by user.id
        """)
    List<Long> findCandidateIds(@Param("date") LocalDate date, @Param("start") Instant start, @Param("end") Instant end,
                                @Param("now") Instant now, Pageable pageable);
}
