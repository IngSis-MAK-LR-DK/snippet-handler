package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

/**
 * Something happened that makes stored snippets need processing again: a user changed their rules,
 * or a version of a language became available. This is the schema of the message on the {@link
 * Streams#REQUESTS} stream; whoever reacts to it only depends on this.
 *
 * @param id identifies the job it creates, so a redelivered message finds it
 * @param userId owner whose snippets are reprocessed; null for all owners
 * @param version target language version, only for {@link ReprocessType#REVALIDATE}
 */
public record ReprocessRequest(
        UUID id, ReprocessType type, String userId, String language, String version) {

    public static ReprocessRequest rulesChanged(String userId, RuleType type, String language) {
        return new ReprocessRequest(
                UUID.randomUUID(), ReprocessType.of(type), userId, language, null);
    }

    public static ReprocessRequest revalidation(String language, String version) {
        return new ReprocessRequest(
                UUID.randomUUID(), ReprocessType.REVALIDATE, null, language, version);
    }

    public Map<String, String> toFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", id.toString());
        fields.put("type", type.name());
        fields.put("language", language);
        if (userId != null) {
            fields.put("userId", userId);
        }
        if (version != null) {
            fields.put("version", version);
        }
        return fields;
    }

    public static ReprocessRequest from(Map<String, String> fields) {
        return new ReprocessRequest(
                UUID.fromString(fields.get("id")),
                ReprocessType.valueOf(fields.get("type")),
                fields.get("userId"),
                fields.get("language"),
                fields.get("version"));
    }
}
