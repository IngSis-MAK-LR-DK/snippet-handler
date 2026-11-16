package edu.austral.ingsis.snippethandler.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.austral.ingsis.snippethandler.reprocessing.Streams;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;
import edu.austral.ingsis.snippethandler.stream.MessageTracing;
import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Against a real Redis, with the same streams and group the handler uses. */
@Testcontainers
class SystemStateReporterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    private final SnippetRepository snippets = mock(SnippetRepository.class);
    private final List<Recorded> recorded = new ArrayList<>();

    record Recorded(String type, Map<String, ?> attributes) {}

    @BeforeAll
    static void connect() {
        factory =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(
                                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @BeforeEach
    void emptyRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private SystemStateReporter reporter(Instant now) {
        return new SystemStateReporter(
                redis,
                snippets,
                (type, attributes) -> recorded.add(new Recorded(type, attributes)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void anEmptyQueueHasNothingWaiting() {
        assertThat(reporter(Instant.now()).queue(Streams.SNIPPET_REPROCESS))
                .containsEntry("stream", Streams.SNIPPET_REPROCESS)
                .containsEntry("length", 0L)
                .containsEntry("pending", 0L)
                .containsEntry("oldestAgeSeconds", 0L);
    }

    @Test
    void saysHowManyMessagesWaitHowManyWereDeliveredAndHowOldTheOldestIs() {
        StreamPublisher publisher = new StreamPublisher(redis, MessageTracing.NONE);
        publisher.publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", "1"));
        publisher.publish(Streams.SNIPPET_REPROCESS, Map.of("taskId", "2"));
        redis.opsForStream()
                .createGroup(Streams.SNIPPET_REPROCESS, ReadOffset.from("0"), Streams.GROUP);
        // Delivered to a consumer that never confirms it.
        redis.opsForStream()
                .read(
                        Consumer.from(Streams.GROUP, "c"),
                        org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                                .count(1),
                        StreamOffset.create(Streams.SNIPPET_REPROCESS, ReadOffset.lastConsumed()));

        Map<String, Object> queue =
                reporter(Instant.now().plusSeconds(90)).queue(Streams.SNIPPET_REPROCESS);

        assertThat(queue).containsEntry("length", 2L).containsEntry("pending", 1L);
        assertThat((Long) queue.get("oldestAgeSeconds")).isBetween(89L, 91L);
    }

    @Test
    void reportsEveryQueueAndHowManySnippetsThereAre() {
        when(snippets.count()).thenReturn(42L);

        reporter(Instant.now()).report();

        assertThat(recorded)
                .extracting(Recorded::type)
                .containsExactly("ReprocessQueue", "ReprocessQueue", "SnippetInventory");
        assertThat(recorded.get(2).attributes().get("total")).isEqualTo(42L);
    }

    @Test
    void aFailingMeasurementDoesNotStopTheOthers() {
        when(snippets.count()).thenThrow(new IllegalStateException("database down"));
        SystemStateReporter reporter =
                new SystemStateReporter(
                        redis,
                        snippets,
                        (type, attributes) -> {
                            if (type.equals("ReprocessQueue")) {
                                throw new IllegalStateException("agent broken");
                            }
                            recorded.add(new Recorded(type, attributes));
                        },
                        Clock.systemUTC());

        reporter.report();

        assertThat(recorded).isEmpty();
    }
}
