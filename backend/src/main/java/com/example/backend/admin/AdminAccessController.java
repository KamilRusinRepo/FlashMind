package com.example.backend.admin;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The entire /api/admin namespace is restricted by SecurityConfiguration. */
@RestController
@RequestMapping("/api/admin")
public class AdminAccessController {
    @GetMapping("/access")
    public ResponseEntity<Void> access() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
