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
        Users users,
        ServiceToken serviceToken,
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

    /** Auth0 Management API, used to search the users a snippet can be shared with. */
    public record Users(String auth0Domain, String clientId, String clientSecret) {
        public Users {
            auth0Domain = auth0Domain == null ? "" : auth0Domain.replaceAll("/+$", "");
        }
    }

    /**
     * Machine to machine client the handler uses towards the language adapters when there is no
     * user token to forward (background work). Without a client id, those requests go without one.
     */
    public record ServiceToken(
            String auth0Domain, String clientId, String clientSecret, String audience) {
        public ServiceToken {
            auth0Domain = auth0Domain == null ? "" : auth0Domain.replaceAll("/+$", "");
        }

        public boolean configured() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Language(String url, List<String> versions, String fileExtension) {
        public Language {
            versions = versions == null ? List.of() : List.copyOf(versions);
        }
    }
}
