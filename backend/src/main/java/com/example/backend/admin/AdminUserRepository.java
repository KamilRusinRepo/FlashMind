package com.example.backend.admin;

import java.util.Arrays;
import java.util.List;
import com.example.backend.admin.dto.response.AdminUserResponse;
import com.example.backend.admin.dto.response.AdminUserPageResponse.Summary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Explicit read projections: never select password hashes or session data. */
@Repository
public class AdminUserRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AdminUserRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String FROM = """
        FROM users u
        WHERE (LOWER(u.username) LIKE LOWER(:pattern) ESCAPE '!' OR LOWER(u.email) LIKE LOWER(:pattern) ESCAPE '!')
          AND (:role = '' OR EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                                    WHERE ur.user_id = u.id AND r.name = :role))
          AND (:status = '' OR u.is_enabled = :enabled)
        """;

    private MapSqlParameterSource parameters(String search, String role, String status) {
        String literal = search.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new MapSqlParameterSource("pattern", "%" + literal + "%").addValue("role", role)
                .addValue("status", status).addValue("enabled", status.equals("active"));
    }

    public long count(String search, String role, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) " + FROM, parameters(search, role, status), Long.class);
    }

    public List<AdminUserResponse> find(String search, String role, String status, int page, int size) {
        return jdbc.query("""
            SELECT u.id, u.username, u.email, u.is_enabled, u.email_verified_at, u.created_at,
                   ARRAY(SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id ORDER BY r.name) AS roles
            """ + FROM + " ORDER BY u.created_at DESC, u.id DESC LIMIT :size OFFSET :offset",
                parameters(search, role, status).addValue("size", size).addValue("offset", (long) page * size),
                (rs, row) -> {
                    var sqlRoles = rs.getArray("roles");
                    try {
                        var roles = Arrays.stream((Object[]) sqlRoles.getArray()).map(Object::toString).toList();
                        return new AdminUserResponse(rs.getLong("id"), rs.getString("username"), rs.getString("email"), roles,
                                rs.getBoolean("is_enabled"), rs.getTimestamp("email_verified_at") != null, rs.getTimestamp("created_at").toInstant());
                    } finally { sqlRoles.free(); }
                });
    }

    public Summary summary() {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS users, COUNT(*) FILTER (WHERE is_enabled) AS active,
                   COUNT(*) FILTER (WHERE NOT is_enabled) AS blocked,
                   (SELECT COUNT(*) FROM decks WHERE is_public = true) AS public_decks FROM users
            """, new MapSqlParameterSource(), (rs, row) -> new Summary(rs.getLong("users"), rs.getLong("active"),
                rs.getLong("blocked"), rs.getLong("public_decks")));
    }
}
