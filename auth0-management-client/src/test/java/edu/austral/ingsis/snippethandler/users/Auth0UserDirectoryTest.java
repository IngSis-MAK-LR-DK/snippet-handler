package edu.austral.ingsis.snippethandler.users;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;

import edu.austral.ingsis.snippethandler.auth0.Auth0ClientCredentialsToken;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class Auth0UserDirectoryTest {

    private static final String USERS_RESPONSE =
            """
            [
              {"user_id": "auth0|ana", "name": "Ana Pérez", "email": "ana@example.com"},
              {"user_id": "google|bob", "email": "bob@example.com"}
            ]
            """;

    private Auth0UserDirectory directory;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        RestClient client =
                RestClient.builder()
                        .baseUrl(wireMock.getHttpBaseUrl())
                        .requestFactory(
                                new JdkClientHttpRequestFactory(
                                        HttpClient.newBuilder()
                                                .version(HttpClient.Version.HTTP_1_1)
                                                .build()))
                        .build();
        Auth0ClientCredentialsToken token =
                new Auth0ClientCredentialsToken(
                        client, "client", "secret", "https://api", Clock.systemUTC());
        directory = new Auth0UserDirectory(client, token);
        stubFor(
                post("/oauth/token")
                        .willReturn(
                                okJson(
                                        "{\"access_token\": \"mgmt-token\", \"expires_in\": 3600}")));
        stubFor(
                get(urlPathEqualTo("/api/v2/users"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(USERS_RESPONSE)));
    }

    @Test
    void mapsAuth0UsersToNameAndFallsBackToEmail() {
        List<UserView> users = directory.search("an", 0, 10);

        assertEquals(
                List.of(
                        new UserView("auth0|ana", "Ana Pérez"),
                        new UserView("google|bob", "bob@example.com")),
                users);
        verify(
                getRequestedFor(urlPathEqualTo("/api/v2/users"))
                        .withHeader("Authorization", equalTo("Bearer mgmt-token"))
                        .withQueryParam("q", equalTo("name:an* OR email:an*"))
                        .withQueryParam("search_engine", equalTo("v3"))
                        .withQueryParam("page", equalTo("0"))
                        .withQueryParam("per_page", equalTo("10")));
    }

    @Test
    void blankQueryDoesNotCallAuth0() {
        assertTrue(directory.search("   ", 0, 10).isEmpty());
        verify(0, getRequestedFor(urlPathEqualTo("/api/v2/users")));
    }

    @Test
    void stripsCharactersThatCouldBreakTheLuceneQuery() {
        directory.search("a\" OR (x", 0, 10);

        verify(
                getRequestedFor(urlPathEqualTo("/api/v2/users"))
                        .withQueryParam("q", equalTo("name:aORx* OR email:aORx*")));
    }

    @Test
    void reusesTheManagementTokenWhileItIsValid() {
        directory.search("an", 0, 10);
        directory.search("an", 1, 10);

        verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    void tokenFailureIsReportedAsDirectoryError() {
        stubFor(post("/oauth/token").willReturn(aResponse().withStatus(401)));

        assertThrows(UserDirectoryException.class, () -> directory.search("an", 0, 10));
    }

    @Test
    void auth0FailureIsReportedAsDirectoryError() {
        stubFor(get(urlPathEqualTo("/api/v2/users")).willReturn(aResponse().withStatus(500)));

        assertThrows(UserDirectoryException.class, () -> directory.search("an", 0, 10));
    }
}
