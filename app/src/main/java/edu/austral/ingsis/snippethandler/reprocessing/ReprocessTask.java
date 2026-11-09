package edu.austral.ingsis.snippethandler.reprocessing;

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

/** State of the reprocessing of one snippet within a {@link ReprocessJob}. */
@Entity
@Table(name = "reprocess_task")
public class ReprocessTask {

    private static final int MAX_ERROR = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", updatable = false)
    private UUID jobId;

    @Column(name = "snippet_id", updatable = false)
    private UUID snippetId;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private int attempts;

    private String error;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ReprocessTask() {}

    public ReprocessTask(UUID jobId, UUID snippetId) {
        this.jobId = jobId;
        this.snippetId = snippetId;
        this.status = TaskStatus.PENDING;
    }

    /** Counted before the work starts, so an attempt that kills the process is not forgotten. */
    public void attempt() {
        attempts++;
    }

    public void done() {
        status = TaskStatus.DONE;
        error = null;
    }

    public void failed(String reason) {
        status = TaskStatus.FAILED;
        recordError(reason);
    }

    public void recordError(String reason) {
        error = reason == null ? null : reason.substring(0, Math.min(reason.length(), MAX_ERROR));
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getSnippetId() {
        return snippetId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getError() {
        return error;
    }
}
