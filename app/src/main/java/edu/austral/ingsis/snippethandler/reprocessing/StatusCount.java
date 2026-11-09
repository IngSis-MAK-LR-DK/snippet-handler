package edu.austral.ingsis.snippethandler.reprocessing;

/** How many tasks of a job are in a status. */
public record StatusCount(TaskStatus status, long total) {}
