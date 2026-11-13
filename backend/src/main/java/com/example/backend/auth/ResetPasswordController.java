package com.example.backend.auth;

import com.example.backend.auth.dto.request.ResetPasswordRequest;
import com.example.backend.auth.dto.response.ResetPasswordResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ResetPasswordController {
    private final ResetPasswordService passwords;

    public ResetPasswordController(ResetPasswordService passwords) { this.passwords = passwords; }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResetPasswordResponse(true));
    }
}
