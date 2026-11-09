package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

/**
 * A user changed their lint or format rules for a language. This is the schema of the message on
 * the {@code rules-changed} stream; whoever reacts to it only depends on this.
 */
public record RulesChanged(UUID id, String userId, RuleType type, String language) {

    public static RulesChanged of(String userId, RuleType type, String language) {
        return new RulesChanged(UUID.randomUUID(), userId, type, language);
    }

    public Map<String, String> toFields() {
        return Map.of(
                "id", id.toString(), "userId", userId, "type", type.name(), "language", language);
    }

    public static RulesChanged from(Map<String, String> fields) {
        return new RulesChanged(
                UUID.fromString(fields.get("id")),
                fields.get("userId"),
                RuleType.valueOf(fields.get("type")),
                fields.get("language"));
    }
}
