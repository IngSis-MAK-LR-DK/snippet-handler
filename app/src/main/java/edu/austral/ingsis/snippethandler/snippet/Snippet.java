package edu.austral.ingsis.snippethandler.snippet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.Violation;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Snippet metadata. The source itself lives in the asset store under {@link #getId()}. */
@Entity
@Table(name = "snippet")
public class Snippet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String description;

    private String language;

    @Column(name = "language_version")
    private String languageVersion;

    @Column(name = "owner_id", updatable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    private Compliance compliance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lint_violations")
    private List<Violation> lintViolations;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Snippet() {}

    public Snippet(
            String name,
            String description,
            String language,
            String languageVersion,
            String ownerId) {
        this.name = name;
        this.description = description;
        this.language = language;
        this.languageVersion = languageVersion;
        this.ownerId = ownerId;
        this.compliance = Compliance.PENDING;
        this.lintViolations = new ArrayList<>();
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeLanguage(String language, String languageVersion) {
        this.language = language;
        this.languageVersion = languageVersion;
    }

    public void lintPassed(List<Violation> violations) {
        this.lintViolations = new ArrayList<>(violations);
        this.compliance = violations.isEmpty() ? Compliance.COMPLIANT : Compliance.NOT_COMPLIANT;
    }

    public void lintFailed() {
        this.lintViolations = new ArrayList<>();
        this.compliance = Compliance.FAILED;
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLanguage() {
        return language;
    }

    public String getLanguageVersion() {
        return languageVersion;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Compliance getCompliance() {
        return compliance;
    }

    public List<Violation> getLintViolations() {
        return List.copyOf(lintViolations);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
