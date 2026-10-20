package edu.austral.ingsis.snippethandler.language;

/** A rule offered by a language, together with its default configuration. */
public record RuleDefinition(
        String name, String description, boolean enabledByDefault, String defaultValue) {

    public RuleConfig toDefaultConfig() {
        return new RuleConfig(name, enabledByDefault, defaultValue);
    }
}
