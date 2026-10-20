package edu.austral.ingsis.snippethandler.snippet;

import edu.austral.ingsis.snippethandler.permission.Relation;

/** Listing criteria; null fields do not filter. */
public record SnippetFilter(
        Relation relation, String name, String language, Compliance compliance) {

    public SnippetFilter {
        relation = relation == null ? Relation.ALL : relation;
    }
}
