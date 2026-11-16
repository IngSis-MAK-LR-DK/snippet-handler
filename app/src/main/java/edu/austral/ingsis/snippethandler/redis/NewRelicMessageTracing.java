package edu.austral.ingsis.snippethandler.redis;

import java.util.HashMap;
import java.util.Map;

import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TransportType;
import org.springframework.stereotype.Component;

/**
 * Distributed tracing through Redis with the New Relic agent: the trace headers ({@code
 * traceparent}, {@code newrelic}...) travel as fields of the message. Without the agent the API
 * does nothing, so this is safe to run anywhere (tests, local).
 */
@Component
public class NewRelicMessageTracing implements MessageTracing {

    @Override
    public Map<String, String> outgoing() {
        ConcurrentHashMapHeaders headers = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
        NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(headers);
        Map<String, String> fields = new HashMap<>();
        for (String name : headers.getHeaderNames()) {
            fields.put(PREFIX + name, headers.getHeader(name));
        }
        return fields;
    }

    @Override
    public void handling(String stream, Map<String, String> fields) {
        // One transaction name per stream in APM ("Stream/snippet-reprocess"), not one per message.
        NewRelic.setTransactionName("Stream", stream);
        Map<String, String> trace = new HashMap<>();
        fields.forEach(
                (name, value) -> {
                    if (name.startsWith(PREFIX)) {
                        trace.put(name.substring(PREFIX.length()), value);
                    }
                });
        if (!trace.isEmpty()) {
            NewRelic.getAgent()
                    .getTransaction()
                    .acceptDistributedTraceHeaders(
                            TransportType.Other,
                            ConcurrentHashMapHeaders.buildFromFlatMap(HeaderType.MESSAGE, trace));
        }
    }
}
