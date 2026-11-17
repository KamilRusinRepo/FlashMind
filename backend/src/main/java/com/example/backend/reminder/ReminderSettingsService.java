package com.example.backend.reminder;

import com.example.backend.auth.SessionUser;
import com.example.backend.user.User;
import com.example.backend.user.UserRepository;
import com.example.backend.reminder.dto.response.ReminderSettingsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderSettingsService {
    private final UserRepository users;
    private final ReminderProperties properties;
    public ReminderSettingsService(UserRepository users, ReminderProperties properties) { this.users = users; this.properties = properties; }
    @Transactional(readOnly = true)
    public ReminderSettingsResponse get(SessionUser principal) {
        var user = requireActive(users.findById(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)), principal);
        return response(user);
    }
    @Transactional
    public ReminderSettingsResponse update(SessionUser principal, boolean enabled) {
        var user = requireActive(users.findByIdForUpdate(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)), principal);
        user.setDailyRemindersEnabled(enabled);
        users.saveAndFlush(user);
        return response(user);
    }
    private User requireActive(User user, SessionUser principal) {
        if (user.getSessionVersion() != principal.sessionVersion()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!user.isEnabled() || user.getEmailVerifiedAt() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
    private ReminderSettingsResponse response(User user) {
        return new ReminderSettingsResponse(user.isDailyRemindersEnabled(), properties.sendHour(), properties.zone());
    }
}
