package edu.austral.ingsis.snippethandler.testcase;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.events.SnippetUpdateHandler;

import org.springframework.stereotype.Component;

/**
 * US 16: when a snippet changes, every one of its tests runs again in the background. The update
 * itself never depends on the outcome.
 */
@Component
public class SnippetTestsRunner implements SnippetUpdateHandler {

    private final TestCaseService tests;

    public SnippetTestsRunner(TestCaseService tests) {
        this.tests = tests;
    }

    @Override
    public void onSnippetUpdated(UUID snippetId) {
        tests.runAll(snippetId);
    }
}
