package edu.austral.ingsis.snippethandler.revalidation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One revalidation of every snippet of a language against a version of that language. */
@Entity
@Table(name = "revalidation_run")
public class RevalidationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String language;

    @Column(name = "language_version")
    private String languageVersion;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    private int total;

    private int processed;

    private int failed;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RevalidationRun() {}

    public RevalidationRun(String language, String languageVersion, int total) {
        this.language = language;
        this.languageVersion = languageVersion;
        this.total = total;
        this.status = RunStatus.RUNNING;
    }

    /** Counts one snippet as handled; {@code failed} tells whether it could not be revalidated. */
    public void advance(boolean failed) {
        processed++;
        if (failed) {
            this.failed++;
        }
    }

    public void complete() {
        status = RunStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getLanguage() {
        return language;
    }

    public String getLanguageVersion() {
        return languageVersion;
    }

    public RunStatus getStatus() {
        return status;
    }

    public int getTotal() {
        return total;
    }

    public int getProcessed() {
        return processed;
    }

    public int getFailed() {
        return failed;
    }

    public enum RunStatus {
        RUNNING,
        COMPLETED
    }
}
