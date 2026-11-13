package com.example.backend.auth;

import com.example.backend.auth.dto.request.ForgotPasswordRequest;
import com.example.backend.auth.dto.response.ForgotPasswordResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ForgotPasswordController {
    private final ForgotPasswordService passwords;

    public ForgotPasswordController(ForgotPasswordService passwords) { this.passwords = passwords; }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> requestReset(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.requestReset(request.email());
        // Keep the same response for missing, blocked, unverified and rate-limited accounts.
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new ForgotPasswordResponse(
                "If an eligible account exists for this address, a password reset link will be sent. Please check your inbox and spam folder and use the latest email.",
                ForgotPasswordService.COOLDOWN_SECONDS));
    }
}
