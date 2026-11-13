package com.example.backend.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;

/** A session stores this projection, never the JPA entity or a password hash. */
public record SessionUser(Long id, String username, String email, List<String> roles, long sessionVersion) implements Principal, Serializable {
    public SessionUser { roles = List.copyOf(roles); }

    @Override
    public String getName() { return username; }
}
