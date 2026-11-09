package edu.austral.ingsis.snippethandler.reprocessing;

import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.stream.StreamPublisher;

import org.springframework.stereotype.Component;

/** Publishes rule changes on the {@link Streams#RULES_CHANGED} stream. */
@Component
public class RedisRulesChangedPublisher implements RulesChangedPublisher {

    private final StreamPublisher streams;

    public RedisRulesChangedPublisher(StreamPublisher streams) {
        this.streams = streams;
    }

    @Override
    public void rulesChanged(String userId, RuleType type, String language) {
        streams.publish(Streams.RULES_CHANGED, RulesChanged.of(userId, type, language).toFields());
    }
}
