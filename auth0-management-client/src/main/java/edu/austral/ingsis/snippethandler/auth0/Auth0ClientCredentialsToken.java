package edu.austral.ingsis.snippethandler.auth0;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Access token for an API, obtained from Auth0 with the client credentials grant (machine to
 * machine) and reused until shortly before it expires. The given client must have the Auth0 domain
 * as base URL.
 */
public class Auth0ClientCredentialsToken {

    private static final Duration SAFETY_MARGIN = Duration.ofMinutes(1);

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.MIN;

    public Auth0ClientCredentialsToken(
            RestClient client, String clientId, String clientSecret, String audience, Clock clock) {
        this.client = client;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.clock = clock;
    }

    public synchronized String get() {
        if (token == null || !clock.instant().isBefore(expiresAt)) {
            refresh();
        }
        return token;
    }

    private void refresh() {
        try {
            TokenResponse response =
                    client.post()
                            .uri("/oauth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(
                                    new TokenRequest(
                                            "client_credentials", clientId, clientSecret, audience))
                            .retrieve()
                            .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new Auth0TokenException("Auth0 returned no token for " + audience, null);
            }
            token = response.accessToken();
            expiresAt = clock.instant().plusSeconds(response.expiresIn()).minus(SAFETY_MARGIN);
        } catch (RestClientException e) {
            throw new Auth0TokenException("Could not get an Auth0 token for " + audience, e);
        }
    }

    record TokenRequest(
            @JsonProperty("grant_type") String grantType,
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            String audience) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}
