package edu.austral.ingsis.snippethandler.testcase;

import edu.austral.ingsis.snippethandler.snippet.SnippetUpdated;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * US 16: when a snippet changes, every one of its tests runs again in the background, once the
 * update is committed. The update itself never depends on the outcome.
 */
@Component
public class SnippetTestsRunner {

    private final TestCaseService tests;

    public SnippetTestsRunner(TestCaseService tests) {
        this.tests = tests;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSnippetUpdated(SnippetUpdated event) {
        tests.runAll(event.snippetId());
    }
}
