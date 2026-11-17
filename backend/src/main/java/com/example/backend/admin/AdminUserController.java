package com.example.backend.admin;

import com.example.backend.admin.dto.request.SetUserEnabledRequest;
import com.example.backend.admin.dto.response.AdminUserPageResponse;
import com.example.backend.admin.dto.response.AdminUserResponse;
import com.example.backend.auth.SessionUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService users;
    public AdminUserController(AdminUserService users) { this.users = users; }

    @GetMapping
    public ResponseEntity<AdminUserPageResponse> list(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(name = "size", defaultValue = "5") @Min(1) @Max(50) int size,
            @RequestParam(name = "search", defaultValue = "") @Size(max = 120) String search,
            @RequestParam(name = "role", defaultValue = "") @Size(max = 20) String role,
            @RequestParam(name = "status", defaultValue = "") @Size(max = 20) String status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.list(page, size, search, role, status));
    }

    @PutMapping("/{userId}/enabled")
    public ResponseEntity<AdminUserResponse> setEnabled(@AuthenticationPrincipal SessionUser principal,
            @PathVariable(name = "userId") @Min(1) Long userId, @Valid @RequestBody SetUserEnabledRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.setEnabled(principal, userId, request.enabled()));
    }
}
