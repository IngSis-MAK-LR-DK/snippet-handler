package edu.austral.ingsis.snippethandler.reprocessing;

import java.time.Instant;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Reprocessing of every snippet of a user in a language after a change of rules. */
@Entity
@Table(name = "reprocess_job")
public class ReprocessJob {

    @Id private UUID id;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private RuleType type;

    private String language;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ReprocessJob() {}

    /** The id is the one of the {@link RulesChanged} that caused it, so a redelivery finds it. */
    public ReprocessJob(UUID id, String userId, RuleType type, String language) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.language = language;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public RuleType getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
