package edu.austral.ingsis.snippethandler.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Tests run without the agent: the API must then do nothing, and never break the messages. */
class NewRelicMessageTracingTest {

    private final NewRelicMessageTracing tracing = new NewRelicMessageTracing();

    @Test
    void withoutTheAgentNothingIsAddedToTheMessages() {
        assertThat(tracing.outgoing()).isEmpty();
    }

    @Test
    void withoutTheAgentHandlingAMessageWithATraceIsHarmless() {
        assertThatCode(
                        () ->
                                tracing.handling(
                                        "s",
                                        Map.of(
                                                "taskId",
                                                "1",
                                                MessageTracing.PREFIX + "traceparent",
                                                "00-abc")))
                .doesNotThrowAnyException();
    }
}
