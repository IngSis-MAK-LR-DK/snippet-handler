package edu.austral.ingsis.snippethandler.languagecatalog;

import edu.austral.ingsis.snippethandler.language.LanguageService;

/** A language together with the version of it a snippet is written in. */
public record LanguageSelection(LanguageService language, String version) {}
