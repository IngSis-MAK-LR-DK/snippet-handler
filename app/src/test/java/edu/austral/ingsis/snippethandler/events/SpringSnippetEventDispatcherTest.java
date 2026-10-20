package edu.austral.ingsis.snippethandler.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SpringSnippetEventDispatcherTest {

    @Test
    void failingHandlerDoesNotStopTheOthers() {
        UUID id = UUID.randomUUID();
        List<UUID> received = new ArrayList<>();
        SnippetUpdateHandler failing =
                snippetId -> {
                    throw new IllegalStateException("boom");
                };
        SpringSnippetEventDispatcher dispatcher =
                new SpringSnippetEventDispatcher(List.of(failing, received::add));

        dispatcher.dispatch(new SnippetUpdated(id));

        assertThat(received).containsExactly(id);
    }
}
