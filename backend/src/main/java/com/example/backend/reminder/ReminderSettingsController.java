package com.example.backend.reminder;

import com.example.backend.auth.SessionUser;
import com.example.backend.reminder.dto.request.UpdateRemindersRequest;
import com.example.backend.reminder.dto.response.ReminderSettingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReminderSettingsController {
    private final ReminderSettingsService settings;
    public ReminderSettingsController(ReminderSettingsService settings) { this.settings = settings; }

    @GetMapping("/api/users/me/reminders")
    public ResponseEntity<ReminderSettingsResponse> get(@AuthenticationPrincipal SessionUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.get(user));
    }
    @PutMapping("/api/users/me/reminders")
    public ResponseEntity<ReminderSettingsResponse> update(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody UpdateRemindersRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.update(user, request.enabled()));
    }
}
