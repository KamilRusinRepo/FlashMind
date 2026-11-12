package com.example.backend.auth;

import com.example.backend.auth.dto.request.ResendVerificationRequest;
import com.example.backend.auth.dto.response.ResendVerificationResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ResendVerificationController {
    private final ResendVerificationService verification;

    public ResendVerificationController(ResendVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ResendVerificationResponse> resend(@Valid @RequestBody ResendVerificationRequest request) {
        verification.resend(request.email());
        // Do not reveal account existence, verification/block status or remaining account cooldown.
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new ResendVerificationResponse(
                "If this address needs verification, a new confirmation link will be sent. Please use the latest email.",
                ResendVerificationService.COOLDOWN_SECONDS));
    }
}
