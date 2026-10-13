package edu.austral.ingsis.snippethandler.testcase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestCaseRequest(
        @NotBlank String name,
        @NotNull List<@NotNull String> inputs,
        @NotNull List<@NotNull String> expectedOutputs) {

    // Nulls are kept as they are so bean validation reports them.
    public TestCaseRequest {
        inputs = unmodifiable(inputs);
        expectedOutputs = unmodifiable(expectedOutputs);
    }

    private static List<String> unmodifiable(List<String> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
