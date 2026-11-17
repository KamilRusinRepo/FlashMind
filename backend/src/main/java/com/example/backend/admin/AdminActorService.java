package com.example.backend.admin;

import com.example.backend.auth.SessionUser;
import com.example.backend.role.RoleName;
import com.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Recheck mutations under the actor's row lock. Call inside the mutation transaction. */
@Service
public class AdminActorService {
    private final UserRepository users;
    public AdminActorService(UserRepository users) { this.users = users; }

    public void requireActiveForUpdate(SessionUser principal) {
        var actor = users.findByIdForUpdate(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (actor.getSessionVersion() != principal.sessionVersion()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!actor.isEnabled() || actor.getEmailVerifiedAt() == null || actor.getRoles().stream().noneMatch(role -> role.getName() == RoleName.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
