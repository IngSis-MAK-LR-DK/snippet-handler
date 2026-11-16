package edu.austral.ingsis.snippethandler.observability;

import java.util.Map;

/**
 * Records a measurement as an event of the monitoring tool, queryable by its attributes (in New
 * Relic: {@code SELECT latest(length) FROM ReprocessQueue FACET stream}).
 */
@FunctionalInterface
public interface CustomEvents {

    void record(String type, Map<String, ?> attributes);
}
