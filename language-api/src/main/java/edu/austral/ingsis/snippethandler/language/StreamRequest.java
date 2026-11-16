package edu.austral.ingsis.snippethandler.language;

import java.util.List;

/**
 * What to run.
 *
 * @param inputs used in order each time the snippet asks for one
 * @param interactive when the given inputs run out: true asks for more through {@link
 *     StreamListener#inputRequested}; false fails the run
 * @param bearerToken token of whoever runs it, forwarded to the language adapter
 */
public record StreamRequest(
        String source,
        String version,
        List<String> inputs,
        boolean interactive,
        String bearerToken) {

    public StreamRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
