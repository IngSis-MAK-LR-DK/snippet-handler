package edu.austral.ingsis.snippethandler.auth0;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class Auth0ClientCredentialsTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private RestClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        client =
                RestClient.builder()
                        .baseUrl(wireMock.getHttpBaseUrl())
                        .requestFactory(
                                new JdkClientHttpRequestFactory(
                                        HttpClient.newBuilder()
                                                .version(HttpClient.Version.HTTP_1_1)
                                                .build()))
                        .build();
        stubFor(
                post("/oauth/token")
                        .willReturn(okJson("{\"access_token\": \"m2m\", \"expires_in\": 3600}")));
    }

    private Auth0ClientCredentialsToken token(Clock clock) {
        return new Auth0ClientCredentialsToken(client, "client", "secret", "https://api", clock);
    }

    @Test
    void requestsATokenForTheAudienceWithClientCredentials() {
        assertEquals("m2m", token(Clock.fixed(NOW, ZoneOffset.UTC)).get());

        verify(
                postRequestedFor(urlPathEqualTo("/oauth/token"))
                        .withRequestBody(
                                equalToJson(
                                        """
                                        {"grant_type": "client_credentials", "client_id": "client",
                                         "client_secret": "secret", "audience": "https://api"}
                                        """)));
    }

    @Test
    void reusesTheTokenUntilShortlyBeforeItExpires() {
        MutableClock clock = new MutableClock(NOW);
        Auth0ClientCredentialsToken token = token(clock);

        token.get();
        clock.advance(Duration.ofMinutes(58));
        token.get();
        verify(1, postRequestedFor(urlPathEqualTo("/oauth/token")));

        clock.advance(Duration.ofMinutes(1));
        token.get();
        verify(2, postRequestedFor(urlPathEqualTo("/oauth/token")));
    }

    @Test
    void refusedRequestIsReportedAsTokenError() {
        stubFor(post("/oauth/token").willReturn(aResponse().withStatus(401)));

        assertThrows(
                Auth0TokenException.class, () -> token(Clock.fixed(NOW, ZoneOffset.UTC)).get());
    }

    @Test
    void responseWithoutTokenIsReportedAsTokenError() {
        stubFor(post("/oauth/token").willReturn(okJson("{\"expires_in\": 3600}")));

        assertThrows(
                Auth0TokenException.class, () -> token(Clock.fixed(NOW, ZoneOffset.UTC)).get());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
