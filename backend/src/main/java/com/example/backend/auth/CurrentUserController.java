package com.example.backend.auth;

import com.example.backend.auth.dto.response.CurrentUserResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(@AuthenticationPrincipal SessionUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUserResponse(user.id(), user.username(), user.email(), user.roles()));
    }
}
