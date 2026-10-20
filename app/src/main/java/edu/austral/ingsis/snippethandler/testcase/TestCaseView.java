package edu.austral.ingsis.snippethandler.testcase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TestCaseView(
        UUID id,
        UUID snippetId,
        String name,
        List<String> inputs,
        List<String> expectedOutputs,
        TestStatus lastStatus,
        Instant lastRunAt) {

    public static TestCaseView of(TestCase test) {
        return new TestCaseView(
                test.getId(),
                test.getSnippetId(),
                test.getName(),
                test.getInputs(),
                test.getExpectedOutputs(),
                test.getLastStatus(),
                test.getLastRunAt());
    }
}
