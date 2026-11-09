package edu.austral.ingsis.snippethandler.stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Member of a consumer group of a Redis stream. Each poll first takes over the messages that were
 * delivered to someone (maybe a consumer that died, maybe a failed attempt of this one) and have
 * stayed unacknowledged for {@code claimIdle}, and then reads the new ones. That is what makes the
 * processing resume after a crash and retry after a failure.
 */
public class StreamConsumer implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(StreamConsumer.class);
    private static final int BATCH = 50;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;
    private final String stream;
    private final String group;
    private final String name;
    private final Duration claimIdle;
    private final Duration pollInterval;
    private final StreamMessageHandler handler;

    private volatile boolean running;
    private volatile boolean groupReady;
    private Thread thread;

    public StreamConsumer(
            StringRedisTemplate redis,
            String stream,
            String group,
            String name,
            Duration claimIdle,
            Duration pollInterval,
            StreamMessageHandler handler) {
        this.redis = redis;
        this.stream = stream;
        this.group = group;
        this.name = name;
        this.claimIdle = claimIdle;
        this.pollInterval = pollInterval;
        this.handler = handler;
    }

    /** Handles what is available now; returns how many messages it went through. */
    public int pollOnce() {
        ensureGroup();
        int seen = 0;
        for (MapRecord<String, String, String> record : claimStale()) {
            seen += process(record);
        }
        List<MapRecord<String, String, String>> fresh =
                redis.<String, String>opsForStream()
                        .read(
                                Consumer.from(group, name),
                                StreamReadOptions.empty().count(BATCH),
                                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        if (fresh != null) {
            for (MapRecord<String, String, String> record : fresh) {
                seen += process(record);
            }
        }
        return seen;
    }

    private int process(MapRecord<String, String, String> record) {
        Map<String, String> fields = record.getValue();
        try {
            handler.handle(fields);
        } catch (RuntimeException e) {
            LOG.warn(
                    "{}: message {} failed, it will be delivered again", stream, record.getId(), e);
            return 1;
        }
        redis.opsForStream().acknowledge(stream, group, record.getId());
        return 1;
    }

    private List<MapRecord<String, String, String>> claimStale() {
        PendingMessages pending =
                redis.opsForStream().pending(stream, group, Range.unbounded(), BATCH);
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<RecordId> stale = new ArrayList<>();
        for (PendingMessage message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().compareTo(claimIdle) >= 0) {
                stale.add(message.getId());
            }
        }
        if (stale.isEmpty()) {
            return List.of();
        }
        List<MapRecord<String, String, String>> claimed =
                redis.<String, String>opsForStream()
                        .claim(stream, group, name, claimIdle, stale.toArray(RecordId[]::new));
        return claimed == null ? List.of() : claimed;
    }

    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redis.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>)
                            connection ->
                                    connection
                                            .streamCommands()
                                            .xGroupCreate(
                                                    redis.getStringSerializer().serialize(stream),
                                                    group,
                                                    ReadOffset.from("0"),
                                                    true));
        } catch (RuntimeException e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")
                    && !String.valueOf(e.getCause()).contains("BUSYGROUP")) {
                throw e;
            }
        }
        groupReady = true;
    }

    private void loop() {
        while (running) {
            try {
                if (pollOnce() == 0) {
                    Thread.sleep(pollInterval.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                if (!running) {
                    return; // interrupted by stop() in the middle of a Redis call
                }
                LOG.error("{}: could not poll the stream, retrying", stream, e);
                try {
                    Thread.sleep(RETRY_BACKOFF.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "stream-consumer-" + stream);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
