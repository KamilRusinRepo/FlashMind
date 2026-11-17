package com.example.backend.user;

import com.example.backend.auth.dto.response.CurrentUserResponse;
import com.example.backend.user.dto.request.UpdateUsernameRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {
    private final UserRepository users;

    public UserProfileService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrent(Long userId) {
        return response(requireEnabled(users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))));
    }

    @Transactional
    public CurrentUserResponse updateUsername(Long userId, UpdateUsernameRequest request) {
        var user = requireEnabled(users.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        if (users.existsByUsernameIgnoreCaseAndIdNot(request.username(), userId)) throw new UsernameConflictException();
        user.setUsername(request.username());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // The unique index also protects simultaneous updates and registrations.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint && "uq_users_username".equals(constraint.getConstraintName())) {
                    throw new UsernameConflictException();
                }
            }
            throw exception;
        }
        return response(user);
    }

    private User requireEnabled(User user) {
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    private CurrentUserResponse response(User user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRoles().stream().map(role -> role.getName().name()).sorted().toList());
    }
}
