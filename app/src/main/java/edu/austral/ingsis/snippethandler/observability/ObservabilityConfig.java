package edu.austral.ingsis.snippethandler.observability;

import java.time.Clock;

import edu.austral.ingsis.snippethandler.snippet.SnippetRepository;

import com.newrelic.api.agent.NewRelic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Periodic report of the state of the system to New Relic. Without the agent the events go nowhere.
 * It can be turned off with {@code snippet.observability.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "snippet.observability.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ObservabilityConfig {

    @Bean
    CustomEvents customEvents() {
        return (type, attributes) ->
                NewRelic.getAgent().getInsights().recordCustomEvent(type, attributes);
    }

    @Bean
    SystemStateReporter systemStateReporter(
            StringRedisTemplate redis, SnippetRepository snippets, CustomEvents events) {
        return new SystemStateReporter(redis, snippets, events, Clock.systemUTC());
    }
}
