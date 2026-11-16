package edu.austral.ingsis.snippethandler.language.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RunningSnippet;
import edu.austral.ingsis.snippethandler.language.StreamListener;
import edu.austral.ingsis.snippethandler.language.StreamRequest;

import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSocketStreamRunnerTest {

    private static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();
    private static volatile String authorization;
    private static volatile Script script;
    private static Tomcat tomcat;
    private static int port;

    private final Recorder events = new Recorder();

    /** What the fake adapter does with each message it gets. */
    @FunctionalInterface
    interface Script {
        void onMessage(Session session, String message) throws IOException;
    }

    @BeforeAll
    static void startFakeAdapter() throws Exception {
        File base = Files.createTempDirectory("fake-adapter").toFile();
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setPort(0);
        Context context = tomcat.addContext("", base.getAbsolutePath());
        context.addServletContainerInitializer(new WsSci(), null);
        // El filtro de WebSocket solo corre si algun servlet atiende el path.
        Tomcat.addServlet(context, "default", new HttpServlet() {});
        context.addServletMappingDecoded("/", "default");
        tomcat.getConnector();
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
        ServerContainer container =
                (ServerContainer)
                        context.getServletContext().getAttribute(ServerContainer.class.getName());
        container.addEndpoint(
                ServerEndpointConfig.Builder.create(FakeAdapter.class, "/execute/stream")
                        .configurator(
                                new ServerEndpointConfig.Configurator() {
                                    @Override
                                    public void modifyHandshake(
                                            ServerEndpointConfig config,
                                            HandshakeRequest request,
                                            HandshakeResponse response) {
                                        List<String> auth =
                                                request.getHeaders().get("Authorization");
                                        authorization = auth == null ? null : auth.get(0);
                                    }
                                })
                        .build());
    }

    @AfterAll
    static void stopFakeAdapter() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    @BeforeEach
    void clear() {
        RECEIVED.clear();
        authorization = null;
    }

    @Test
    void anInteractiveRunReportsEveryEventAndSendsTheInputs() throws Exception {
        script =
                (session, message) -> {
                    if (message.contains("\"start\"")) {
                        session.getBasicRemote()
                                .sendText("{\"type\":\"output\",\"line\":\"hola\"}");
                        session.getBasicRemote()
                                .sendText("{\"type\":\"input\",\"prompt\":\"nombre\"}");
                    } else {
                        session.getBasicRemote()
                                .sendText("{\"type\":\"output\",\"line\":\"chau Ana\"}");
                        session.getBasicRemote().sendText("{\"type\":\"done\",\"errors\":[]}");
                        session.close();
                    }
                };

        RunningSnippet run =
                runner().stream(
                        new StreamRequest("code", "1.1", List.of("x"), true, "tok"), events);

        assertThat(events.next()).isEqualTo("output:hola");
        assertThat(events.next()).isEqualTo("input:nombre");
        run.input("Ana");
        assertThat(events.next()).isEqualTo("output:chau Ana");
        assertThat(events.next()).isEqualTo("finished:[]");
        assertThat(authorization).isEqualTo("Bearer tok");
        String start = RECEIVED.poll(5, TimeUnit.SECONDS);
        assertThat(start)
                .contains("\"type\":\"start\"")
                .contains("\"content\":\"code\"")
                .contains("\"version\":\"1.1\"")
                .contains("\"inputs\":[\"x\"]")
                .contains("\"interactive\":true");
        assertThat(RECEIVED.poll(5, TimeUnit.SECONDS))
                .contains("\"type\":\"input\"")
                .contains("\"value\":\"Ana\"");
    }

    @Test
    void theErrorsOfTheRunArriveWhenItEnds() throws Exception {
        script =
                (session, message) -> {
                    session.getBasicRemote()
                            .sendText("{\"type\":\"done\",\"errors\":[\"division by zero\"]}");
                    session.close();
                };

        runner().stream(new StreamRequest("code", "1.1", null, false, null), events);

        assertThat(events.next()).isEqualTo("finished:[division by zero]");
        assertThat(authorization).isNull();
    }

    @Test
    void aStreamClosedBeforeTheEndFinishesWithAnError() throws Exception {
        script =
                (session, message) ->
                        session.close(
                                new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "nope"));

        runner().stream(new StreamRequest("code", "1.1", List.of(), false, "t"), events);

        assertThat(events.next()).startsWith("finished:").contains("closed the stream");
        assertThat(events.nothingMore()).isTrue();
    }

    @Test
    void anInvalidMessageFinishesTheRun() throws Exception {
        script =
                (session, message) -> {
                    session.getBasicRemote().sendText("{\"type\":\"something-new\"}");
                    session.getBasicRemote().sendText("not json");
                };

        runner().stream(new StreamRequest("code", "1.1", List.of(), false, "t"), events);

        assertThat(events.next()).startsWith("finished:").contains("invalid message");
    }

    @Test
    void cancellingFinishesTheRun() throws Exception {
        script = (session, message) -> {};

        RunningSnippet run =
                runner().stream(new StreamRequest("code", "1.1", List.of(), true, "t"), events);
        run.cancel();

        assertThat(events.next()).isEqualTo("finished:[The run was cancelled]");
    }

    @Test
    void anAdapterThatIsNotThereFailsRightAway() throws IOException {
        int closedPort;
        try (var socket = new java.net.ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        WebSocketStreamRunner nowhere =
                new WebSocketStreamRunner(
                        "printscript",
                        URI.create("ws://localhost:" + closedPort + "/execute/stream"),
                        HttpClient.newHttpClient());

        assertThatThrownBy(
                        () ->
                                nowhere.stream(
                                        new StreamRequest("c", "1.1", List.of(), false, null),
                                        events))
                .isInstanceOf(LanguageServiceException.class)
                .hasMessageContaining("printscript");
    }

    @Test
    void theStreamUriComesFromTheAdapterBaseUrl() {
        assertThat(WebSocketStreamRunner.streamUriOf("http://snippet-printscript:8080"))
                .isEqualTo(URI.create("ws://snippet-printscript:8080/execute/stream"));
        assertThat(WebSocketStreamRunner.streamUriOf("https://adapter.example.com/"))
                .isEqualTo(URI.create("wss://adapter.example.com/execute/stream"));
    }

    private WebSocketStreamRunner runner() {
        return new WebSocketStreamRunner(
                "printscript",
                URI.create("ws://localhost:" + port + "/execute/stream"),
                HttpClient.newHttpClient());
    }

    /** The fake adapter: records what it gets and lets the test's script answer. */
    public static class FakeAdapter extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(
                    String.class,
                    (MessageHandler.Whole<String>)
                            message -> {
                                RECEIVED.add(message);
                                try {
                                    script.onMessage(session, message);
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
        }
    }

    private static final class Recorder implements StreamListener {
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

        @Override
        public void output(String line) {
            events.add("output:" + line);
        }

        @Override
        public void inputRequested(String prompt) {
            events.add("input:" + prompt);
        }

        @Override
        public void finished(List<String> errors) {
            events.add("finished:" + errors);
        }

        String next() throws InterruptedException {
            String event = events.poll(5, TimeUnit.SECONDS);
            assertThat(event).as("an event arrived").isNotNull();
            return event;
        }

        boolean nothingMore() throws InterruptedException {
            return events.poll(300, TimeUnit.MILLISECONDS) == null;
        }
    }
}
