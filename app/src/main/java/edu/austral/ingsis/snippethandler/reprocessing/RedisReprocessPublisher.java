package edu.austral.ingsis.snippethandler.reprocessing;

import edu.austral.ingsis.snippethandler.redis.RedisStreamPublisher;

import org.springframework.stereotype.Component;

/** Publishes requests on the {@link Streams#REQUESTS} stream. */
@Component
public class RedisReprocessPublisher implements ReprocessPublisher {

    private final RedisStreamPublisher streams;

    public RedisReprocessPublisher(RedisStreamPublisher streams) {
        this.streams = streams;
    }

    @Override
    public void request(ReprocessRequest request) {
        streams.publish(Streams.REQUESTS, request.toFields());
    }
}
