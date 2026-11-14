package com.example.backend.study;

import java.net.URI;
import com.example.backend.auth.SessionUser;
import com.example.backend.study.dto.response.StudySessionResponse;
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
