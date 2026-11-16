package edu.austral.ingsis.snippethandler.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class OutboundCallLoggingTest {

    private final AtomicLong nanos = new AtomicLong();
    private final OutboundCallLogging logging =
            new OutboundCallLogging(Duration.ofSeconds(1), nanos::get);
    private final MockClientHttpRequest request =
            new MockClientHttpRequest(
                    HttpMethod.GET,
                    URI.create("https://tenant.auth0.com/api/v2/users?q=email:ana@mail.com*"));

    private final Logger logger = (Logger) LoggerFactory.getLogger(OutboundCallLogging.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    private ClientHttpRequestExecution answering(HttpStatus status, Duration took) {
        return (req, body) -> {
            nanos.addAndGet(took.toNanos());
            return new MockClientHttpResponse(new byte[0], status);
        };
    }

    @Test
    void aFastSuccessfulCallIsNotLogged() throws IOException {
        logging.intercept(request, new byte[0], answering(HttpStatus.OK, Duration.ofMillis(50)));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void aServerErrorIsLoggedWithWhereItWentAndHowLongItTook() throws IOException {
        logging.intercept(
                request,
                new byte[0],
                answering(HttpStatus.SERVICE_UNAVAILABLE, Duration.ofMillis(80)));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("call failed GET tenant.auth0.com/api/v2/users status=503 tookMs=80");
    }

    @Test
    void aSlowCallIsLoggedEvenIfItSucceeded() throws IOException {
        logging.intercept(request, new byte[0], answering(HttpStatus.OK, Duration.ofMillis(1500)));

        assertThat(appender.list)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("slow call GET tenant.auth0.com/api/v2/users status=200 tookMs=1500");
    }

    @Test
    void aCallWithNoResponseIsLoggedAndStillFails() {
        ClientHttpRequestExecution down =
                (req, body) -> {
                    throw new IOException("Connection refused");
                };

        assertThrows(IOException.class, () -> logging.intercept(request, new byte[0], down));

        assertThat(appender.list)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .startsWith("call failed GET tenant.auth0.com/api/v2/users")
                .contains("Connection refused");
    }

    @Test
    void theQueryStringNeverReachesTheLog() throws IOException {
        logging.intercept(request, new byte[0], answering(HttpStatus.BAD_GATEWAY, Duration.ZERO));

        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("ana@mail.com");
    }
}
