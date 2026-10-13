package edu.austral.ingsis.snippethandler.events;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Receiving end of the in-process transport: once the transaction that published the event commits,
 * hands it to every {@link SnippetUpdateHandler} on a background thread. A failing handler does not
 * stop the others.
 */
@Component
public class SpringSnippetEventDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SpringSnippetEventDispatcher.class);

    private final List<SnippetUpdateHandler> handlers;

    public SpringSnippetEventDispatcher(List<SnippetUpdateHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(SnippetUpdated event) {
        for (SnippetUpdateHandler handler : handlers) {
            try {
                handler.onSnippetUpdated(event.snippetId());
            } catch (RuntimeException e) {
                LOG.error(
                        "{} failed for snippet {}",
                        handler.getClass().getSimpleName(),
                        event.snippetId(),
                        e);
            }
        }
    }
}
