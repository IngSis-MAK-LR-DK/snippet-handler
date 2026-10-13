package edu.austral.ingsis.snippethandler.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration under {@code snippet.*}: where every collaborating service lives. */
@ConfigurationProperties("snippet")
public record SnippetProperties(
        String defaultLanguage,
        Cors cors,
        Asset asset,
        Permission permission,
        Map<String, Language> languages) {

    public SnippetProperties {
        languages = languages == null ? Map.of() : Map.copyOf(languages);
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Asset(String url, String container) {}

    public record Permission(String url) {}

    public record Language(String url, List<String> versions, String fileExtension) {
        public Language {
            versions = versions == null ? List.of() : List.copyOf(versions);
        }
    }
}
