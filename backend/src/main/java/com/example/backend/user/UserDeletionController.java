package com.example.backend.user;

import com.example.backend.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserDeletionController {
    private final UserDeletionService deletion;

    public UserDeletionController(UserDeletionService deletion) { this.deletion = deletion; }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal SessionUser user,
                                             HttpServletRequest request, HttpServletResponse response) {
        // Identity comes from the authenticated session, never a user ID in the request body.
        deletion.deleteAccount(user.id());
        new SecurityContextLogoutHandler().logout(request, response, null);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, null);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
