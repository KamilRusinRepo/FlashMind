package com.example.backend.user;

import com.example.backend.auth.SessionUser;
import com.example.backend.user.dto.request.ChangePasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordChangeController {
    private final PasswordChangeService passwords;
    public PasswordChangeController(PasswordChangeService passwords) { this.passwords = passwords; }

    @PostMapping("/api/users/me/password")
    public ResponseEntity<Void> change(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ChangePasswordRequest input,
                                      HttpServletRequest request, HttpServletResponse response) {
        passwords.change(user, input);
        // Only log out the current HTTP session after the database transaction committed.
        new SecurityContextLogoutHandler().logout(request, response, null);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, null);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
