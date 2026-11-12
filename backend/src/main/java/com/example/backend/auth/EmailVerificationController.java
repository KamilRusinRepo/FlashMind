package com.example.backend.auth;

import com.example.backend.auth.dto.request.VerifyEmailRequest;
import com.example.backend.auth.dto.response.VerifyEmailResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {
    private final EmailVerificationService verification;

    public EmailVerificationController(EmailVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verify(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.verify(request.token()));
    }
}
