package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.stream.StreamConsumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Wires the two stages of the reprocessing to their streams. */
@Configuration
@ConditionalOnProperty(
        name = "snippet.reprocessing.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ReprocessingConfig {

    private final String consumerName = "handler-" + UUID.randomUUID();

    @Bean
    StreamConsumer rulesChangedConsumer(
            StringRedisTemplate redis,
            ReprocessingFanOut fanOut,
            ReprocessingProperties properties) {
        return consumer(redis, Streams.RULES_CHANGED, properties, fanOut::handle);
    }

    @Bean
    StreamConsumer snippetReprocessConsumer(
            StringRedisTemplate redis,
            SnippetReprocessor reprocessor,
            ReprocessingProperties properties) {
        return consumer(redis, Streams.SNIPPET_REPROCESS, properties, reprocessor::handle);
    }

    private StreamConsumer consumer(
            StringRedisTemplate redis,
            String stream,
            ReprocessingProperties properties,
            edu.austral.ingsis.snippethandler.stream.StreamMessageHandler handler) {
        return new StreamConsumer(
                redis,
                stream,
                Streams.GROUP,
                consumerName,
                properties.claimIdle(),
                properties.pollInterval(),
                handler);
    }
}
