package edu.austral.ingsis.snippethandler.stream;

import java.util.HashMap;
import java.util.Map;

import edu.austral.ingsis.snippethandler.observability.RequestIdFilter;

import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Appends messages to a Redis stream. Producers only know the stream and the message schema; the
 * current trace and request id are added to every message so its handling joins the same trace and
 * its log lines are found with the id of the request that caused it.
 */
@Component
public class StreamPublisher {

    /** Field of the message with the request id of whoever published it. */
    public static final String REQUEST_ID = "requestId";

    private final StringRedisTemplate redis;
    private final MessageTracing tracing;

    public StreamPublisher(StringRedisTemplate redis, MessageTracing tracing) {
        this.redis = redis;
        this.tracing = tracing;
    }

    public void publish(String stream, Map<String, String> fields) {
        Map<String, String> message = new HashMap<>(tracing.outgoing());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            message.put(REQUEST_ID, requestId);
        }
        message.putAll(fields);
        redis.opsForStream().add(StreamRecords.string(message).withStreamKey(stream));
    }
}
