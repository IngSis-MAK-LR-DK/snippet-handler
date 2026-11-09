package edu.austral.ingsis.snippethandler.reprocessing;

/** Asks for stored snippets to be processed again in the background. */
public interface ReprocessPublisher {

    void request(ReprocessRequest request);
}
