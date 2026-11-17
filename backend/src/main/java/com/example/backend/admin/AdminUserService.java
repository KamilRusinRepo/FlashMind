package com.example.backend.admin;

import java.util.Set;
import com.example.backend.admin.dto.response.AdminUserPageResponse;
import com.example.backend.admin.dto.response.AdminUserResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.UserRepository;
import com.example.backend.user.UserDeletionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final AdminUserRepository listing;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AdminActorService actor;
    private final UserDeletionService deletion;
    public AdminUserService(AdminUserRepository listing, UserRepository users, RoleRepository roles, AdminActorService actor, UserDeletionService deletion) {
        this.listing = listing; this.users = users; this.roles = roles; this.actor = actor;
        this.deletion = deletion;
    }

    @Transactional
    public void delete(SessionUser principal, Long targetId) {
        roles.findByNameForUpdate(RoleName.ADMIN).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        actor.requireActiveForUpdate(principal);
        users.findByIdForUpdate(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        deletion.deleteAccount(targetId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminUserPageResponse list(int page, int size, String search, String role, String status) {
        role = role.strip(); status = status.strip(); search = search.strip();
        if (!Set.of("", "ADMIN", "USER").contains(role) || !Set.of("", "active", "blocked").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        long total = listing.count(search, role, status);
        return new AdminUserPageResponse(listing.find(search, role, status, page, size), page, size, total,
                (int) ((total + size - 1) / size), listing.summary());
    }

    @Transactional
    public AdminUserResponse setEnabled(SessionUser principal, Long targetId, boolean enabled) {
        // Serialize administrative status changes, including competing attempts to block administrators.
        roles.findByNameForUpdate(RoleName.ADMIN).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        actor.requireActiveForUpdate(principal);
        if (!enabled && principal.id().equals(targetId)) throw new AdminUserActionException("You cannot block your own account.");
        var target = users.findByIdForUpdate(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (target.isEnabled() != enabled) {
            boolean activeAdmin = target.getEmailVerifiedAt() != null && target.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
            if (!enabled && activeAdmin && users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN) <= 1) {
                throw new AdminUserActionException("You cannot block the last active administrator.");
            }
            target.setEnabled(enabled);
            target.invalidateSessions();
            users.saveAndFlush(target);
        }
        return AdminUserResponse.from(target);
    }
}
