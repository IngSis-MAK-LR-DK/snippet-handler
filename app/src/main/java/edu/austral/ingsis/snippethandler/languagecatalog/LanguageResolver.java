package edu.austral.ingsis.snippethandler.languagecatalog;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;

import org.springframework.stereotype.Component;

/** Picks the language and version to use when the caller leaves them out. */
@Component
public class LanguageResolver {

    private final LanguageRegistry languages;
    private final String defaultLanguage;

    public LanguageResolver(LanguageRegistry languages, SnippetProperties properties) {
        this.languages = languages;
        this.defaultLanguage = properties.defaultLanguage();
    }

    /** The named language, or the default one ({@code snippet.default-language}) if blank. */
    public LanguageService language(String name) {
        return languages.get(isBlank(name) ? defaultLanguage : name);
    }

    /**
     * @param version a supported version, or blank for the latest one
     * @throws edu.austral.ingsis.snippethandler.language.UnsupportedLanguageException if the
     *     language or the version is not supported
     */
    public LanguageSelection select(String name, String version) {
        LanguageService language = language(name);
        if (isBlank(version)) {
            return new LanguageSelection(language, language.latestVersion());
        }
        return new LanguageSelection(languages.get(language.language(), version), version);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
