package com.example.backend.study;

import java.net.URI;
import com.example.backend.auth.SessionUser;
import com.example.backend.study.dto.response.StudySessionResponse;
import com.example.backend.study.dto.response.StudySummaryResponse;
import com.example.backend.study.dto.response.StudyQuestionResponse;
import com.example.backend.study.dto.request.SubmitStudyAnswerRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudyController {
    private final StudyService study;
    public StudyController(StudyService study) { this.study = study; }

    @PostMapping("/study-sessions/{sessionId}/complete")
    public ResponseEntity<StudySummaryResponse> complete(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "sessionId") Long sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(study.complete(user.id(), sessionId));
    }

    @GetMapping("/study-sessions/{sessionId}/summary")
    public ResponseEntity<StudySummaryResponse> summary(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "sessionId") Long sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(study.getSummary(user.id(), sessionId));
    }

    @PostMapping("/study-sessions/{sessionId}/answers/{answerId}")
    public ResponseEntity<StudyQuestionResponse> submit(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "sessionId") Long sessionId, @PathVariable(name = "answerId") Long answerId,
            @Valid @RequestBody SubmitStudyAnswerRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(study.submit(user.id(), sessionId, answerId, request));
    }

    @PostMapping("/study-sessions/{sessionId}/answers/{answerId}/hint")
    public ResponseEntity<StudyQuestionResponse> hint(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "sessionId") Long sessionId, @PathVariable(name = "answerId") Long answerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(study.hint(user.id(), sessionId, answerId));
    }

    @PostMapping("/decks/{deckId}/study-sessions")
    public ResponseEntity<StudySessionResponse> start(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "deckId") Long deckId) {
        var session = study.start(user.id(), deckId);
        return ResponseEntity.created(URI.create("/api/study-sessions/" + session.id()))
                .cacheControl(CacheControl.noStore()).body(session);
    }

    @GetMapping("/study-sessions/{sessionId}")
    public ResponseEntity<StudySessionResponse> getOwned(@AuthenticationPrincipal SessionUser user,
            @PathVariable(name = "sessionId") Long sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(study.getOwned(user.id(), sessionId));
    }
}
