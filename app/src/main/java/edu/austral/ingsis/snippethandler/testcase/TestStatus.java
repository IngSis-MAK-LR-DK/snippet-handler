package edu.austral.ingsis.snippethandler.testcase;

public enum TestStatus {
    /** Not run since it was created or last changed. */
    PENDING,
    PASSED,
    FAILED,
    /** The snippet could not be executed (e.g. the language adapter is down). */
    ERROR
}
