package edu.austral.ingsis.snippethandler.testcase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A test of a snippet: inputs are fed in order and outputs are compared, in order, with every
 * printed line (US 8).
 */
@Entity
@Table(name = "test_case")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snippet_id", updatable = false)
    private UUID snippetId;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> inputs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_outputs")
    private List<String> expectedOutputs;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status")
    private TestStatus lastStatus;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected TestCase() {}

    public TestCase(
            UUID snippetId, String name, List<String> inputs, List<String> expectedOutputs) {
        this.snippetId = snippetId;
        redefine(name, inputs, expectedOutputs);
    }

    public final void redefine(String name, List<String> inputs, List<String> expectedOutputs) {
        this.name = name;
        this.inputs = new ArrayList<>(inputs);
        this.expectedOutputs = new ArrayList<>(expectedOutputs);
        this.lastStatus = TestStatus.PENDING;
        this.lastRunAt = null;
    }

    public void ran(TestStatus status) {
        this.lastStatus = status;
        this.lastRunAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnippetId() {
        return snippetId;
    }

    public String getName() {
        return name;
    }

    public List<String> getInputs() {
        return List.copyOf(inputs);
    }

    public List<String> getExpectedOutputs() {
        return List.copyOf(expectedOutputs);
    }

    public TestStatus getLastStatus() {
        return lastStatus;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }
}
