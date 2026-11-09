package edu.austral.ingsis.snippethandler.reprocessing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Reprocessing of the stored snippets of a language: of one user after a change of rules, or of all
 * of them after a new version of the language.
 */
@Entity
@Table(name = "reprocess_job")
public class ReprocessJob {

    @Id private UUID id;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private ReprocessType type;

    private String language;

    @Column(name = "language_version")
    private String languageVersion;

    /** Whether the tasks of its snippets were already written. */
    private boolean expanded;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ReprocessJob() {}

    public ReprocessJob(
            UUID id, String userId, ReprocessType type, String language, String languageVersion) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.language = language;
        this.languageVersion = languageVersion;
    }

    /** The job has the id of the request, so a redelivered request finds it. */
    public static ReprocessJob of(ReprocessRequest request) {
        return new ReprocessJob(
                request.id(),
                request.userId(),
                request.type(),
                request.language(),
                request.version());
    }

    public void markExpanded() {
        expanded = true;
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

    public ReprocessType getType() {
        return type;
    }

    public String getLanguage() {
        return language;
    }

    public String getLanguageVersion() {
        return languageVersion;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
