package edu.austral.ingsis.snippethandler.testcase;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of running a test: what the snippet printed against what was expected. {@code testId} is
 * null for a test that is not stored.
 */
public record TestRunResult(
        UUID testId,
        TestStatus status,
        List<String> outputs,
        List<String> expectedOutputs,
        List<String> errors) {}
