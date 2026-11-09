package edu.austral.ingsis.snippethandler.reprocessing;

import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.springframework.stereotype.Component;

/** Publishes requests on the {@link Streams#REQUESTS} stream. */
@Component
public class RedisReprocessPublisher implements ReprocessPublisher {

    private final StreamPublisher streams;

    public RedisReprocessPublisher(StreamPublisher streams) {
        this.streams = streams;
    }

    @Override
    public void request(ReprocessRequest request) {
        streams.publish(Streams.REQUESTS, request.toFields());
    }
}
