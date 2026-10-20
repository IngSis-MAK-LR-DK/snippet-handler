package edu.austral.ingsis.snippethandler.language;

public class UnsupportedLanguageException extends RuntimeException {

    public UnsupportedLanguageException(String language, String version) {
        super(
                version == null
                        ? "Unsupported language '%s'".formatted(language)
                        : "Unsupported version '%s' of language '%s'".formatted(version, language));
    }
}
