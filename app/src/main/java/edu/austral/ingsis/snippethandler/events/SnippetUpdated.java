package edu.austral.ingsis.snippethandler.events;

import java.util.UUID;

/** Spring application event carrying a snippet update; internal to the in-process transport. */
record SnippetUpdated(UUID snippetId) {}
