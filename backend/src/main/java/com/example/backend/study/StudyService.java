package com.example.backend.study;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.study.dto.request.SubmitStudyAnswerRequest;
import com.example.backend.study.dto.response.StudyQuestionResponse;
import com.example.backend.study.dto.response.StudySessionResponse;
import com.example.backend.study.dto.response.StudySummaryResponse;
import com.example.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyService {
    private static final int SESSION_CARD_LIMIT = 10;
    private final StudySessionRepository sessions;
    private final StudyAnswerRepository answers;
    private final FlashcardRepository flashcards;
    private final DeckRepository decks;
    private final UserRepository users;
    private final Clock clock;
    private final FlashcardProgressRepository progress;

    public StudyService(StudySessionRepository sessions, StudyAnswerRepository answers, FlashcardRepository flashcards,
                        DeckRepository decks, UserRepository users, Clock clock, FlashcardProgressRepository progress) {
        this.sessions = sessions;
        this.answers = answers;
        this.flashcards = flashcards;
        this.decks = decks;
        this.users = users;
        this.clock = clock;
        this.progress = progress;
    }

    @Transactional
    public StudySummaryResponse complete(Long userId, Long sessionId) {
        var session = lockOwnedSession(userId, sessionId);
        if (session.getStatus() == StudySessionStatus.ABANDONED) throw new StudyActionException("An abandoned session cannot be completed.");
        var savedAnswers = answers.findBySession_IdOrderByPositionAsc(sessionId);
        requireAllAnswered(session, savedAnswers);
        if (session.isActive()) {
            // PostgreSQL stores microseconds; keep retries identical to the first response.
            session.complete(clock.instant().truncatedTo(ChronoUnit.MICROS));
            sessions.saveAndFlush(session);
        }
        return summaryOf(session, savedAnswers);
    }

    @Transactional(readOnly = true)
    public StudySummaryResponse getSummary(Long userId, Long sessionId) {
        var session = findOwnedSession(userId, sessionId);
        if (session.getStatus() != StudySessionStatus.COMPLETED) throw new StudyActionException("Complete this study session before viewing its summary.");
        var savedAnswers = answers.findBySession_IdOrderByPositionAsc(sessionId);
        requireAllAnswered(session, savedAnswers);
        return summaryOf(session, savedAnswers);
    }

    private void requireAllAnswered(StudySession session, List<StudyAnswer> savedAnswers) {
        if (savedAnswers.size() != session.getTotalCards() || savedAnswers.stream().anyMatch(answer -> !answer.isAnswered())) {
            throw new StudyActionException("Save an answer for every flashcard before completing the session.");
        }
    }

    private StudySummaryResponse summaryOf(StudySession session, List<StudyAnswer> savedAnswers) {
        int total = session.getTotalCards();
        int correct = (int) savedAnswers.stream().filter(answer -> Boolean.TRUE.equals(answer.getCorrect())).count();
        int xp = savedAnswers.stream().mapToInt(answer -> StudyRules.sessionXp(Boolean.TRUE.equals(answer.getCorrect()), answer.isUsedHint())).sum();
        return new StudySummaryResponse(session.getId(), session.getDeck().getId(), session.getDeck().getName(), total,
                correct, total - correct, (int) Math.round(100.0 * correct / total), xp,
                Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds(), session.getStartedAt(), session.getEndedAt(), session.getMode());
    }

    private StudySession findOwnedSession(Long userId, Long sessionId) {
        var session = sessions.findByIdAndUser_Id(sessionId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        decks.findByIdAndOwner_Id(session.getDeck().getId(), userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return session;
    }

    @Transactional
    public StudyQuestionResponse submit(Long userId, Long sessionId, Long answerId, SubmitStudyAnswerRequest request) {
        var session = lockOwnedSession(userId, sessionId);
        var answer = findAnswer(sessionId, answerId);
        if (answer.isAnswered()) {
            if (answer.isSkipped() == request.skipped() && (answer.isSkipped() || answer.getUserAnswer().equals(request.userAnswer()))) {
                return StudyQuestionResponse.from(answer); // Idempotent retry, no second progress update.
            }
            throw new StudyActionException("This answer has already been saved. Refresh the session to see the result.");
        }
        requireCurrentQuestion(session, answer);
        boolean correct = !request.skipped() && StudyRules.matches(request.userAnswer(), answer.getAnswerSnapshot());
        var now = clock.instant();
        answer.recordAnswer(request.userAnswer(), correct, request.skipped(), now);
        if (session.getMode() == StudyMode.SCHEDULED_REVIEW && answer.getFlashcard() != null) {
            var state = progress.findByUser_IdAndFlashcard_Id(userId, answer.getFlashcard().getId())
                    .orElseGet(() -> new FlashcardProgress(session.getUser(), answer.getFlashcard()));
            var plan = StudyRules.schedule(correct, state.getMasteryLevel(), state.getWrongStreak());
            state.recordReview(correct, plan.masteryLevel(), plan.wrongStreak(), now, now.plus(plan.intervalDays(), ChronoUnit.DAYS));
            progress.saveAndFlush(state);
        }
        return StudyQuestionResponse.from(answers.saveAndFlush(answer));
    }

    @Transactional
    public StudyQuestionResponse hint(Long userId, Long sessionId, Long answerId) {
        var session = lockOwnedSession(userId, sessionId);
        var answer = findAnswer(sessionId, answerId);
        if (answer.isAnswered()) throw new StudyActionException("This flashcard has already been answered.");
        requireCurrentQuestion(session, answer);
        answer.markHintUsed();
        return StudyQuestionResponse.from(answers.saveAndFlush(answer));
    }

    private StudySession lockOwnedSession(Long userId, Long sessionId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var session = sessions.findByIdAndUser_Id(sessionId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        decks.findOwnedForUpdate(session.getDeck().getId(), userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return sessions.findOwnedForUpdate(sessionId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private StudyAnswer findAnswer(Long sessionId, Long answerId) {
        return answers.findByIdAndSession_Id(answerId, sessionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void requireCurrentQuestion(StudySession session, StudyAnswer answer) {
        if (!session.isActive()) throw new StudyActionException("This study session has already ended.");
        var first = answers.findFirstBySession_IdAndAnsweredAtIsNullOrderByPositionAsc(session.getId());
        if (first.isEmpty() || !first.get().getId().equals(answer.getId())) {
            throw new StudyActionException("Answer the current flashcard before continuing.");
        }
    }

    @Transactional
    public StudySessionResponse start(Long userId, Long deckId, StudyMode mode) {
        // Same lock order as deck/card mutations and account deletion.
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = decks.findOwnedForUpdate(deckId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var now = clock.instant();
        var cards = mode == StudyMode.FREE_PRACTICE
                ? flashcards.findForFreePractice(deckId, userId, PageRequest.of(0, SESSION_CARD_LIMIT))
                : flashcards.findForStudy(deckId, userId, now, PageRequest.of(0, SESSION_CARD_LIMIT));
        if (cards.isEmpty()) {
            throw new StudyStartException(flashcards.existsByDeck_IdAndDeck_Owner_Id(deckId, userId)
                    ? "No flashcards are due for review yet."
                    : "Add at least one flashcard before starting a study session.");
        }
        var session = sessions.saveAndFlush(new StudySession(user, deck, cards.size(), now, mode));
        var selected = new ArrayList<StudyAnswer>();
        for (int i = 0; i < cards.size(); i++) selected.add(new StudyAnswer(session, cards.get(i), i + 1));
        answers.saveAllAndFlush(selected);
        return new StudySessionResponse(session.getId(), DeckListItemResponse.from(deck), session.getTotalCards(),
                session.getStatus(), session.getStartedAt(), selected.stream().map(StudyQuestionResponse::from).toList(), session.getMode());
    }

    @Transactional(readOnly = true)
    public StudySessionResponse getOwned(Long userId, Long sessionId) {
        var session = findOwnedSession(userId, sessionId);
        var cards = answers.findBySession_IdOrderByPositionAsc(sessionId).stream().map(StudyQuestionResponse::from).toList();
        return new StudySessionResponse(session.getId(), DeckListItemResponse.from(session.getDeck()), session.getTotalCards(),
                session.getStatus(), session.getStartedAt(), cards, session.getMode());
    }
}
