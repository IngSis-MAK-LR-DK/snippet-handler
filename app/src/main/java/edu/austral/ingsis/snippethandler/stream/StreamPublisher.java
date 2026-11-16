package edu.austral.ingsis.snippethandler.stream;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Appends messages to a Redis stream. Producers only know the stream and the message schema; the
 * current trace is added to every message so its handling joins the same trace.
 */
@Component
public class StreamPublisher {

    private final StringRedisTemplate redis;
    private final MessageTracing tracing;

    public StreamPublisher(StringRedisTemplate redis, MessageTracing tracing) {
        this.redis = redis;
        this.tracing = tracing;
    }

    public void publish(String stream, Map<String, String> fields) {
        Map<String, String> message = new HashMap<>(tracing.outgoing());
        message.putAll(fields);
        redis.opsForStream().add(StreamRecords.string(message).withStreamKey(stream));
    }
}
