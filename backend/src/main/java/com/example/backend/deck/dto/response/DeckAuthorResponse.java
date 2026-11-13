package com.example.backend.deck.dto.response;

import com.example.backend.user.User;

/** Use this projection in deck/Explore responses instead of serializing the owner entity. */
public record DeckAuthorResponse(Long id, String displayName) {
    public static DeckAuthorResponse from(User owner) {
        return owner == null ? new DeckAuthorResponse(null, "Deleted user")
                : new DeckAuthorResponse(owner.getId(), owner.getUsername());
    }
}
