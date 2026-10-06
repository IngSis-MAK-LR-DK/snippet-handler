package edu.austral.ingsis.snippethandler.testcase;

import edu.austral.ingsis.snippethandler.events.SnippetUpdatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * US 16: once an update is committed, every test of the snippet runs in the background. The update
 * itself never depends on the outcome.
 */
@Component
public class SnippetTestsRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetTestsRunner.class);

    private final TestCaseService tests;

    public SnippetTestsRunner(TestCaseService tests) {
        this.tests = tests;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnippetUpdated(SnippetUpdatedEvent event) {
        try {
            tests.runAll(event.snippetId());
        } catch (RuntimeException e) {
            LOG.error("Could not run the tests of snippet {}", event.snippetId(), e);
        }
    }
}
