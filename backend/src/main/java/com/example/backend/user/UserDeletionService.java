package com.example.backend.user;

import com.example.backend.deck.DeckRepository;
import com.example.backend.admin.AdminUserActionException;
import com.example.backend.role.RoleName;
import com.example.backend.role.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService {
    private final UserRepository users;
    private final DeckRepository decks;
    private final RoleRepository roles;

    public UserDeletionService(UserRepository users, DeckRepository decks, RoleRepository roles) {
        this.users = users;
        this.decks = decks;
        this.roles = roles;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        // Serialize with administrative blocking/demotion, including self-service deletion.
        roles.findByNameForUpdate(RoleName.ADMIN).orElseThrow();
        var user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        boolean activeAdmin = user.isEnabled() && user.getEmailVerifiedAt() != null
                && user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
        if (activeAdmin && users.countByEnabledTrueAndEmailVerifiedAtIsNotNullAndRoles_Name(RoleName.ADMIN) <= 1) {
            throw new AdminUserActionException("You cannot delete the last active administrator.");
        }
        decks.deletePrivateByOwnerId(userId);
        users.delete(user);
        users.flush();
        // DB cascades remove tokens/role links. Public decks remain with owner_id = NULL.
        // Existing sessions are rejected by SessionVersionFilter because the user no longer exists.
    }
}
