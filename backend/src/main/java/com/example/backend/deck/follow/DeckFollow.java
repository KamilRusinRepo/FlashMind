package com.example.backend.deck.follow;

import com.example.backend.deck.Deck;
import com.example.backend.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "deck_follows", uniqueConstraints = @UniqueConstraint(name = "uq_deck_follow", columnNames = {"user_id", "deck_id"}))
public class DeckFollow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    protected DeckFollow() {}
    public DeckFollow(User user, Deck deck) { this.user = user; this.deck = deck; }
}
