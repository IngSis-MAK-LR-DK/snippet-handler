package edu.austral.ingsis.snippethandler.snippet;

import jakarta.validation.constraints.NotBlank;

public record ShareRequest(@NotBlank String userId) {}
