package com.example.backend.user;

import com.example.backend.auth.SessionUser;
import com.example.backend.auth.dto.request.VerifyEmailRequest;
import com.example.backend.auth.dto.response.VerifyEmailResponse;
import com.example.backend.user.dto.request.RequestEmailChange;
import com.example.backend.user.dto.response.PendingEmailChangeResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailChangeController {
    private final EmailChangeService changes;
    public EmailChangeController(EmailChangeService changes) { this.changes = changes; }

    @GetMapping("/api/users/me/email-change")
    public ResponseEntity<PendingEmailChangeResponse> pending(@AuthenticationPrincipal SessionUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(changes.pending(user.id()));
    }

    @PostMapping("/api/users/me/email-change")
    public ResponseEntity<PendingEmailChangeResponse> request(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody RequestEmailChange request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(changes.request(user.id(), user.sessionVersion(), request));
    }

    @PostMapping("/api/auth/confirm-email-change")
    public ResponseEntity<VerifyEmailResponse> confirm(@Valid @RequestBody VerifyEmailRequest request) {
        changes.confirm(request.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new VerifyEmailResponse(true));
    }
}
