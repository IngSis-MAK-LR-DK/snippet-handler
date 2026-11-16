package edu.austral.ingsis.snippethandler.execution;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.snippet.Snippet;
import edu.austral.ingsis.snippethandler.snippet.SnippetContents;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The UI side of a streamed run through a real WebSocket: the token as subprotocol, the permission
 * check when it opens and the messages both ways. The language adapter is a fake that greets
 * whoever types their name.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SnippetRunWebSocketIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @RegisterExtension
    static final WireMockExtension PERMISSIONS =
            WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://test.auth0.local/");
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.audiences", () -> "snippet-searcher");
        registry.add("snippet.permission.url", PERMISSIONS::baseUrl);
    }

    @TestConfiguration
    static class FakeLanguage {
        @Bean
        @Primary
        LanguageRegistry greetingLanguage() {
            LanguageService language = mock(LanguageService.class);
            when(language.language()).thenReturn("printscript");
            when(language.stream(any(), any()))
                    .thenAnswer(
                            invocation -> {
                                StreamListener listener = invocation.getArgument(1);
                                listener.inputRequested("nombre");
                                return new RunningSnippet() {
                                    @Override
                                    public void input(String value) {
                                        listener.output("hola " + value);
                                        listener.finished(List.of());
                                    }

                                    @Override
                                    public void cancel() {}
                                };
                            });
            return new LanguageRegistry(List.of(language));
        }
    }

    @LocalServerPort private int port;
    @Autowired private SnippetRepository snippets;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SnippetContents contents;

    private UUID snippetId;

    @BeforeEach
    void setUp() {
        PERMISSIONS.resetAll();
        when(jwtDecoder.decode(anyString()))
                .thenReturn(
                        new Jwt(
                                "user-token",
                                Instant.now(),
                                Instant.now().plusSeconds(60),
                                Map.of("alg", "none"),
                                Map.of("sub", "auth0|beto")));
        snippetId =
                snippets.save(new Snippet("s", null, "printscript", "1.1", "auth0|ana")).getId();
        when(contents.read(snippetId)).thenReturn("source");
    }

    @Test
    void someoneWhoCanReadTheSnippetRunsItInteractively() throws Exception {
        role("READER");
        Client client = connect(snippetId, "user-token");

        client.send("{\"type\":\"run\"}");
        assertThat(client.next()).contains("\"type\":\"input\"").contains("nombre");
        client.send("{\"type\":\"input\",\"value\":\"Beto\"}");

        assertThat(client.next()).contains("\"line\":\"hola Beto\"");
        assertThat(client.next()).contains("\"type\":\"done\"");
        assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(WebSocket.NORMAL_CLOSURE);
        assertThat(client.socket.getSubprotocol()).isEqualTo("bearer");
    }

    @Test
    void someoneWithoutAccessCannotOpenIt() {
        role("NONE");

        assertThatThrownBy(() -> connect(snippetId, "user-token"))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void withoutATokenItDoesNotOpen() {
        role("OWNER");

        assertThatThrownBy(() -> connect(snippetId, null))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void aSnippetThatDoesNotExistDoesNotOpen() {
        role("OWNER");

        assertThatThrownBy(() -> connect(UUID.randomUUID(), "user-token"))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }

    @Test
    void aMessageThatIsNotJsonClosesTheConnection() throws Exception {
        role("OWNER");
        Client client = connect(snippetId, "user-token");

        client.send("not json");

        assertThat(client.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }

    private void role(String role) {
        PERMISSIONS.stubFor(
                get(urlPathMatching("/permissions/[0-9a-f-]{36}"))
                        .willReturn(okJson("{\"role\": \"" + role + "\"}")));
    }

    private Client connect(UUID snippet, String token) throws Exception {
        Client client = new Client();
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (token != null) {
            builder.subprotocols("bearer", token);
        }
        client.socket =
                builder.buildAsync(
                                URI.create(
                                        "ws://localhost:%d/snippets/%s/run"
                                                .formatted(port, snippet)),
                                client)
                        .get(5, TimeUnit.SECONDS);
        return client;
    }

    private static final class Client implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        void send(String text) {
            socket.sendText(text, true).join();
        }

        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("a message arrived").isNotNull();
            return message;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }
    }
}
