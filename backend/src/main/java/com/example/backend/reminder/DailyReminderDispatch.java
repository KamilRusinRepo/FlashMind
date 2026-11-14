package com.example.backend.reminder;

import java.time.Instant;
import java.time.LocalDate;
import com.example.backend.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "daily_reminder_dispatches")
public class DailyReminderDispatch {
    public enum Status { PENDING, PUBLISHED, SENT, SKIPPED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false) private User user;
    @Column(name = "reminder_date", nullable = false, updatable = false) private LocalDate reminderDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "sent_at") private Instant sentAt;
    protected DailyReminderDispatch() {}
    public DailyReminderDispatch(User user, LocalDate date, Instant now) {
        this.user = java.util.Objects.requireNonNull(user); reminderDate = java.util.Objects.requireNonNull(date);
        createdAt = java.util.Objects.requireNonNull(now); status = Status.PENDING;
    }
    public Long getId() { return id; }
    public User getUser() { return user; }
    public LocalDate getReminderDate() { return reminderDate; }
    public Status getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
    public void published(Instant now) { status = Status.PUBLISHED; publishedAt = now; }
    public void sent(Instant now) { status = Status.SENT; sentAt = now; }
    public void skip() { status = Status.SKIPPED; }
}
