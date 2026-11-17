package com.example.backend.admin;

import java.util.HashSet;
import java.util.stream.Collectors;
import com.example.backend.admin.dto.request.UpdateAdminUserRequest;
import com.example.backend.admin.dto.response.AdminUserEditResponse;
import com.example.backend.admin.dto.response.AdminUserResponse;
import com.example.backend.auth.SessionUser;
import com.example.backend.auth.token.TokenType;
import com.example.backend.auth.token.UserTokenRepository;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import com.example.backend.user.EmailChangeService;
import com.example.backend.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserEditService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final AdminActorService actor;
    private final UserTokenRepository tokens;
    private final EmailChangeService emailChanges;
    public AdminUserEditService(UserRepository users, RoleRepository roles, AdminActorService actor,
                                UserTokenRepository tokens, EmailChangeService emailChanges) {
        this.users = users; this.roles = roles; this.actor = actor; this.tokens = tokens; this.emailChanges = emailChanges;
    }

    @Transactional
    public AdminUserEditResponse update(SessionUser principal, Long targetId, UpdateAdminUserRequest request) {
        // Shared with block/unblock: concurrent demotions must not remove every active administrator.
        roles.findByNameForUpdate(RoleName.ADMIN).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        actor.requireActiveForUpdate(principal);
        var target = users.findByIdForUpdate(targetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var previousRoles = target.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
        boolean rolesChanged = !previousRoles.equals(request.roles());
        boolean usernameChanged = !target.getUsername().equals(request.username());
        boolean emailRequested = !target.getEmail().equalsIgnoreCase(request.email());
        if (target.isEnabled() && target.getEmailVerifiedAt() != null && previousRoles.contains(RoleName.ADMIN)
                && !request.roles().contains(RoleName.ADMIN)
                && users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN) <= 1) {
            throw new AdminUserEditException("roles", "You cannot remove the last active administrator role.");
        }
        if (users.existsByUsernameIgnoreCaseAndIdNot(request.username(), targetId)) {
            throw new AdminUserEditException("username", "This username is already taken.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(request.email(), targetId)) {
            throw new AdminUserEditException("email", "This email address is already registered.");
        }
        target.setUsername(request.username());
        for (var role : new HashSet<>(target.getRoles())) if (!request.roles().contains(role.getName())) target.removeRole(role);
        for (var name : request.roles()) if (!previousRoles.contains(name)) {
            target.addRole(roles.findByName(name).orElseThrow(() -> new IllegalStateException("A required role is missing.")));
        }
        boolean invalidate = rolesChanged || usernameChanged;
        if (invalidate) target.invalidateSessions();
        try { users.saveAndFlush(target); }
        catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint) {
                    if ("uq_users_username".equals(constraint.getConstraintName())) throw new AdminUserEditException("username", "This username is already taken.");
                    if ("uq_users_email".equals(constraint.getConstraintName())) throw new AdminUserEditException("email", "This email address is already registered.");
                }
            }
            throw exception;
        }
        if (rolesChanged) for (var type : TokenType.values()) tokens.deleteUnusedByUserIdAndType(targetId, type);
        String pendingEmail = emailRequested ? emailChanges.requestByAdmin(targetId, request.email()).pendingEmail() : null;
        return new AdminUserEditResponse(AdminUserResponse.from(target), pendingEmail, invalidate);
    }
}
