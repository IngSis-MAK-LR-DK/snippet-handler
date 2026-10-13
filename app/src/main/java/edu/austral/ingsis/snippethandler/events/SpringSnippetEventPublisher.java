package edu.austral.ingsis.snippethandler.events;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** In-process transport: snippet events travel as Spring application events. */
@Component
public class SpringSnippetEventPublisher implements SnippetEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringSnippetEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void snippetUpdated(UUID snippetId) {
        publisher.publishEvent(new SnippetUpdated(snippetId));
    }
}
