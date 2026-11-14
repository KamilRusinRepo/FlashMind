package com.example.backend.study;

import java.time.Clock;
import java.util.ArrayList;
import com.example.backend.deck.DeckRepository;
import com.example.backend.deck.dto.response.DeckListItemResponse;
import com.example.backend.flashcard.FlashcardRepository;
import com.example.backend.study.dto.response.StudyQuestionResponse;
import com.example.backend.study.dto.response.StudySessionResponse;
import com.example.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyService {
    private final StudySessionRepository sessions;
    private final StudyAnswerRepository answers;
    private final FlashcardRepository flashcards;
    private final DeckRepository decks;
    private final UserRepository users;
    private final Clock clock;

    public StudyService(StudySessionRepository sessions, StudyAnswerRepository answers, FlashcardRepository flashcards,
                        DeckRepository decks, UserRepository users, Clock clock) {
        this.sessions = sessions;
        this.answers = answers;
        this.flashcards = flashcards;
        this.decks = decks;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public StudySessionResponse start(Long userId, Long deckId) {
        // Same lock order as deck/card mutations and account deletion.
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var deck = decks.findOwnedForUpdate(deckId, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var now = clock.instant();
        var cards = flashcards.findForStudy(deckId, userId, now, PageRequest.of(0, 10));
        if (cards.isEmpty()) {
            throw new StudyStartException(flashcards.existsByDeck_IdAndDeck_Owner_Id(deckId, userId)
                    ? "No flashcards are due for review yet."
                    : "Add at least one flashcard before starting a study session.");
        }
        var session = sessions.saveAndFlush(new StudySession(user, deck, cards.size(), now));
        var selected = new ArrayList<StudyAnswer>();
        for (int i = 0; i < cards.size(); i++) selected.add(new StudyAnswer(session, cards.get(i), i + 1));
        answers.saveAllAndFlush(selected);
        return new StudySessionResponse(session.getId(), DeckListItemResponse.from(deck), session.getTotalCards(),
                session.getStatus(), session.getStartedAt(), selected.stream().map(StudyQuestionResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public StudySessionResponse getOwned(Long userId, Long sessionId) {
        var session = sessions.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // An owner-only study route is not a way to read another person's public deck.
        var deck = decks.findByIdAndOwner_Id(session.getDeck().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var cards = answers.findBySession_IdOrderByPositionAsc(sessionId).stream().map(StudyQuestionResponse::from).toList();
        return new StudySessionResponse(session.getId(), DeckListItemResponse.from(deck), session.getTotalCards(),
                session.getStatus(), session.getStartedAt(), cards);
    }
}
