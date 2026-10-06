package edu.austral.ingsis.snippethandler.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LanguageRegistryTest {

    private final LanguageService printScript =
            new FakeLanguage("PrintScript", Set.of("1.0", "1.1"));
    private final LanguageRegistry registry = new LanguageRegistry(List.of(printScript));

    @Test
    void resolvesLanguageIgnoringCase() {
        assertSame(printScript, registry.get("printscript", "1.1"));
        assertSame(printScript, registry.get("PRINTSCRIPT"));
    }

    @Test
    void rejectsUnknownLanguage() {
        UnsupportedLanguageException e =
                assertThrows(UnsupportedLanguageException.class, () -> registry.get("go", "1.0"));
        assertEquals("Unsupported language 'go'", e.getMessage());
    }

    @Test
    void rejectsNullLanguage() {
        assertThrows(UnsupportedLanguageException.class, () -> registry.get(null));
    }

    @Test
    void rejectsUnknownVersion() {
        UnsupportedLanguageException e =
                assertThrows(
                        UnsupportedLanguageException.class,
                        () -> registry.get("printscript", "2.0"));
        assertEquals("Unsupported version '2.0' of language 'printscript'", e.getMessage());
    }

    @Test
    void listsAllLanguages() {
        assertEquals(List.of(printScript), registry.all());
    }

    private record FakeLanguage(String language, Set<String> versions) implements LanguageService {

        @Override
        public String fileExtension() {
            return "ps";
        }

        @Override
        public ValidationResult validate(String source, String version) {
            return ValidationResult.ok();
        }

        @Override
        public List<Violation> lint(String source, String version, List<RuleConfig> rules) {
            return List.of();
        }

        @Override
        public String format(String source, String version, List<RuleConfig> rules) {
            return source;
        }

        @Override
        public ExecutionResult execute(String source, String version, List<String> inputs) {
            return new ExecutionResult(List.of(), List.of());
        }

        @Override
        public List<RuleDefinition> defaultRules(RuleType type) {
            return List.of();
        }
    }
}
