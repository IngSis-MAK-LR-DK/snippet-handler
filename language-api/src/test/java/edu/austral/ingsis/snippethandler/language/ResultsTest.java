package edu.austral.ingsis.snippethandler.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResultsTest {

    @Test
    void validationResultFactories() {
        assertTrue(ValidationResult.ok().valid());
        SyntaxError error = new SyntaxError("missing-semicolon", "Expected ';'", 2, 10);
        ValidationResult invalid = ValidationResult.invalid(List.of(error));
        assertFalse(invalid.valid());
        assertEquals(List.of(error), invalid.errors());
        assertEquals(List.of(), new ValidationResult(true, null).errors());
    }

    @Test
    void executionResultIsSuccessfulWithoutErrors() {
        assertTrue(new ExecutionResult(List.of("hi"), null).successful());
        assertFalse(new ExecutionResult(null, List.of("boom")).successful());
        assertEquals(List.of(), new ExecutionResult(null, null).outputs());
    }

    @Test
    void ruleDefinitionBuildsDefaultConfig() {
        RuleDefinition definition = new RuleDefinition("indentation", "Spaces", true, "4");
        assertEquals(new RuleConfig("indentation", true, "4"), definition.toDefaultConfig());
    }
}
