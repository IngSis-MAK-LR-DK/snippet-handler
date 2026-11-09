package edu.austral.ingsis.snippethandler.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Against a real Redis: delivery, acknowledgement, retry of failures and resuming after a crash.
 */
@Testcontainers
class StreamConsumerTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static StreamPublisher publisher;

    @BeforeAll
    static void connect() {
        factory =
                new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(
                                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        publisher = new StreamPublisher(redis);
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @Test
    void deliversAndAcknowledgesEveryMessage() {
        String stream = stream();
        List<String> seen = new ArrayList<>();
        StreamConsumer consumer = collecting(stream, "a", Duration.ofMillis(50), seen::add);
        publisher.publish(stream, Map.of("n", "1"));
        publisher.publish(stream, Map.of("n", "2"));

        assertThat(consumer.pollOnce()).isEqualTo(2);

        assertThat(seen).containsExactly("1", "2");
        assertThat(consumer.pollOnce()).isZero();
        assertThat(
                        redis.opsForStream()
                                .pending(
                                        stream,
                                        "g",
                                        org.springframework.data.domain.Range.unbounded(),
                                        10))
                .isEmpty();
    }

    @Test
    void acknowledgedMessagesAreRemovedFromTheStreamButPendingOnesStay() {
        String stream = stream();
        StreamConsumer consumer =
                consumer(
                        stream,
                        "a",
                        Duration.ofMinutes(5),
                        fields -> {
                            if (fields.get("n").equals("bad")) {
                                throw new IllegalStateException("boom");
                            }
                        });
        publisher.publish(stream, Map.of("n", "1"));
        publisher.publish(stream, Map.of("n", "2"));
        publisher.publish(stream, Map.of("n", "bad"));

        consumer.pollOnce();

        assertThat(redis.opsForStream().size(stream)).isEqualTo(1);
    }

    @Test
    void aFailedMessageIsDeliveredAgainAfterTheClaimTime() throws InterruptedException {
        String stream = stream();
        List<String> attempts = new ArrayList<>();
        StreamConsumer consumer =
                consumer(
                        stream,
                        "a",
                        Duration.ofMillis(100),
                        fields -> {
                            attempts.add(fields.get("n"));
                            if (attempts.size() == 1) {
                                throw new IllegalStateException("boom");
                            }
                        });
        publisher.publish(stream, Map.of("n", "1"));

        consumer.pollOnce();
        Thread.sleep(150);
        consumer.pollOnce();

        assertThat(attempts).containsExactly("1", "1");
        assertThat(consumer.pollOnce()).isZero();
    }

    @Test
    void anotherConsumerResumesWhatADeadOneLeftUnacknowledged() throws InterruptedException {
        String stream = stream();
        StreamConsumer dead =
                consumer(
                        stream,
                        "dead",
                        Duration.ofMillis(100),
                        fields -> {
                            throw new IllegalStateException("crashed mid-way");
                        });
        publisher.publish(stream, Map.of("n", "7"));
        dead.pollOnce();

        List<String> seen = new ArrayList<>();
        StreamConsumer survivor = collecting(stream, "survivor", Duration.ofMillis(100), seen::add);
        assertThat(survivor.pollOnce()).isZero(); // too soon to take it over
        Thread.sleep(150);
        survivor.pollOnce();

        assertThat(seen).containsExactly("7");
    }

    @Test
    void theBackgroundLoopKeepsConsumingUntilStopped() {
        String stream = stream();
        List<String> seen = new CopyOnWriteArrayList<>();
        StreamConsumer consumer = collecting(stream, "loop", Duration.ofMillis(50), seen::add);

        consumer.start();
        try {
            assertThat(consumer.isRunning()).isTrue();
            publisher.publish(stream, Map.of("n", "late"));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> seen.contains("late"));
        } finally {
            consumer.stop();
        }
        assertThat(consumer.isRunning()).isFalse();
    }

    private static StreamConsumer collecting(
            String stream,
            String name,
            Duration claimIdle,
            java.util.function.Consumer<String> onN) {
        return consumer(stream, name, claimIdle, fields -> onN.accept(fields.get("n")));
    }

    private static StreamConsumer consumer(
            String stream, String name, Duration claimIdle, StreamMessageHandler handler) {
        return new StreamConsumer(
                redis, stream, "g", name, claimIdle, Duration.ofMillis(20), handler);
    }

    private static String stream() {
        return "test-" + UUID.randomUUID();
    }
}
