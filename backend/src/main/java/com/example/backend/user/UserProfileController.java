package com.example.backend.user;

import com.example.backend.auth.SessionUser;
import com.example.backend.auth.dto.response.CurrentUserResponse;
import com.example.backend.user.dto.request.UpdateUsernameRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProfileController {
    private final UserProfileService profiles;
    public UserProfileController(UserProfileService profiles) { this.profiles = profiles; }

    @PutMapping("/api/users/me/username")
    public ResponseEntity<CurrentUserResponse> updateUsername(@AuthenticationPrincipal SessionUser user,
            @Valid @RequestBody UpdateUsernameRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.updateUsername(user.id(), request));
    }
}
