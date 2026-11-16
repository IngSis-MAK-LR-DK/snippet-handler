package edu.austral.ingsis.snippethandler.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import edu.austral.ingsis.snippethandler.reprocessing.Streams;
import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reports, every so often, what no request shows: how much background work is waiting and how many
 * snippets there are.
 *
 * <ul>
 *   <li>{@code ReprocessQueue} per stream: {@code length} (messages not processed yet), {@code
 *       pending} (delivered but not confirmed) and {@code oldestAgeSeconds} (how long the oldest
 *       one has been waiting). A queue that does not get shorter, or whose oldest message keeps
 *       getting older, is stuck: that is the saturation signal.
 *   <li>{@code SnippetInventory}: {@code total} snippets. Answers "how many were there yesterday?"
 *       when snippets go missing.
 * </ul>
 *
 * Processed messages are deleted from the stream, so the first one left is the oldest waiting.
 */
public class SystemStateReporter {

    private static final Logger LOG = LoggerFactory.getLogger(SystemStateReporter.class);
    private static final List<String> STREAMS =
            List.of(Streams.REQUESTS, Streams.SNIPPET_REPROCESS);

    private final StringRedisTemplate redis;
    private final SnippetRepository snippets;
    private final CustomEvents events;
    private final Clock clock;

    public SystemStateReporter(
            StringRedisTemplate redis,
            SnippetRepository snippets,
            CustomEvents events,
            Clock clock) {
        this.redis = redis;
        this.snippets = snippets;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${snippet.observability.report-interval:60s}",
            initialDelayString = "${snippet.observability.report-interval:60s}")
    public void report() {
        for (String stream : STREAMS) {
            try {
                events.record("ReprocessQueue", queue(stream));
            } catch (RuntimeException e) {
                // Redis down is exactly when this matters, but the report must not kill the job.
                LOG.warn("Could not measure the stream {}: {}", stream, e.toString());
            }
        }
        try {
            events.record("SnippetInventory", Map.of("total", snippets.count()));
        } catch (RuntimeException e) {
            LOG.warn("Could not count the snippets: {}", e.toString());
        }
    }

    Map<String, Object> queue(String stream) {
        Long length = redis.opsForStream().size(stream);
        return Map.of(
                "stream",
                stream,
                "length",
                length == null ? 0 : length,
                "pending",
                pending(stream),
                "oldestAgeSeconds",
                oldestAgeSeconds(stream));
    }

    private long pending(String stream) {
        try {
            PendingMessagesSummary summary = redis.opsForStream().pending(stream, Streams.GROUP);
            return summary == null ? 0 : summary.getTotalPendingMessages();
        } catch (RuntimeException e) {
            // The group does not exist until the consumer starts: nothing delivered yet.
            return 0;
        }
    }

    private long oldestAgeSeconds(String stream) {
        List<MapRecord<String, Object, Object>> first =
                redis.opsForStream().range(stream, Range.unbounded(), Limit.limit().count(1));
        if (first == null || first.isEmpty()) {
            return 0;
        }
        // An entry id is "<milliseconds when it was added>-<sequence>".
        Instant added = Instant.ofEpochMilli(first.get(0).getId().getTimestamp());
        return Math.max(0, Duration.between(added, clock.instant()).toSeconds());
    }
}
