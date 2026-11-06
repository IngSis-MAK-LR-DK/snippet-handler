package edu.austral.ingsis.snippethandler.users;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import edu.austral.ingsis.snippethandler.auth0.Auth0ClientCredentialsToken;
import edu.austral.ingsis.snippethandler.auth0.Auth0TokenException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link UserDirectory} backed by the Auth0 Management API (GET /api/v2/users). The given client
 * must have the Auth0 domain as base URL.
 */
public class Auth0UserDirectory implements UserDirectory {

    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^\\p{L}\\p{N}@._-]");

    private final RestClient client;
    private final Auth0ClientCredentialsToken token;

    /**
     * @param token token for the Management API ({@code https://<domain>/api/v2/})
     */
    public Auth0UserDirectory(RestClient client, Auth0ClientCredentialsToken token) {
        this.client = client;
        this.token = token;
    }

    @Override
    public List<UserView> search(String query, int page, int size) {
        // Lucene syntax: only plain characters reach the query, so nothing can break it.
        String term = UNSAFE_CHARACTERS.matcher(query == null ? "" : query.trim()).replaceAll("");
        if (term.isEmpty()) {
            return List.of();
        }
        try {
            String bearer = token.get();
            AuthUser[] users =
                    client.get()
                            .uri(
                                    uri ->
                                            uri.path("/api/v2/users")
                                                    .queryParam(
                                                            "q",
                                                            "name:%1$s* OR email:%1$s*"
                                                                    .formatted(term))
                                                    .queryParam("search_engine", "v3")
                                                    .queryParam("page", page)
                                                    .queryParam("per_page", size)
                                                    .queryParam("include_fields", true)
                                                    .queryParam("fields", "user_id,name,email")
                                                    .build())
                            .headers(headers -> headers.setBearerAuth(bearer))
                            .retrieve()
                            .body(AuthUser[].class);
            if (users == null) {
                return List.of();
            }
            return Arrays.stream(users)
                    .map(user -> new UserView(user.userId(), displayName(user)))
                    .toList();
        } catch (RestClientException | Auth0TokenException e) {
            throw new UserDirectoryException("Could not search users in Auth0", e);
        }
    }

    private static String displayName(AuthUser user) {
        if (user.name() != null && !user.name().isBlank()) {
            return user.name();
        }
        return user.email() != null ? user.email() : user.userId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthUser(@JsonProperty("user_id") String userId, String name, String email) {}
}
