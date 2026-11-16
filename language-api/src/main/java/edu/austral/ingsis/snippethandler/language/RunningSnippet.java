package edu.austral.ingsis.snippethandler.language;

/** A run in progress. */
public interface RunningSnippet {

    /** Answers the input the snippet is waiting for. */
    void input(String value);

    /** Stops the run: nobody is waiting for it anymore. */
    void cancel();
}
