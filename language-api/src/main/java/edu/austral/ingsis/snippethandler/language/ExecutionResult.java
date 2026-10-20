package edu.austral.ingsis.snippethandler.language;

import java.util.List;

/** Outputs printed by a snippet run, in print order, plus any runtime error. */
public record ExecutionResult(List<String> outputs, List<String> errors) {

    public ExecutionResult {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean successful() {
        return errors.isEmpty();
    }
}
