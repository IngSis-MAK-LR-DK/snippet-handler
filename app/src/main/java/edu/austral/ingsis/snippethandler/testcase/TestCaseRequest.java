package edu.austral.ingsis.snippethandler.testcase;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TestCaseRequest(
        @NotBlank String name,
        @NotNull List<@NotNull String> inputs,
        @NotNull List<@NotNull String> expectedOutputs) {}
