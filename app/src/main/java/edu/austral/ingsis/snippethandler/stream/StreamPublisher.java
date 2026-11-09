package edu.austral.ingsis.snippethandler.stream;

import java.util.Map;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Appends messages to a Redis stream. Producers only know the stream and the message schema. */
@Component
public class StreamPublisher {

    private final StringRedisTemplate redis;

    public StreamPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publish(String stream, Map<String, String> fields) {
        redis.opsForStream().add(StreamRecords.string(fields).withStreamKey(stream));
    }
}
