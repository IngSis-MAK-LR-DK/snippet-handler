package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public record ValidationResult(boolean valid, List<SyntaxError> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(List<SyntaxError> errors) {
        return new ValidationResult(false, errors);
    }
}
