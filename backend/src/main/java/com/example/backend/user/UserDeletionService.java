package com.example.backend.user;

import com.example.backend.deck.DeckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService {
    private final UserRepository users;
    private final DeckRepository decks;

    public UserDeletionService(UserRepository users, DeckRepository decks) {
        this.users = users;
        this.decks = decks;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        // Match the lock order used by verification/reset: owner first, tokens second.
        var user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null) return;
        decks.deletePrivateByOwnerId(userId);
        users.delete(user);
        users.flush();
        // DB cascades remove tokens/role links. Public decks remain with owner_id = NULL.
        // Existing sessions are rejected by SessionVersionFilter because the user no longer exists.
    }
}
