package edu.austral.ingsis.snippethandler.language;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves the {@link LanguageService} that handles a given language and version. */
public class LanguageRegistry {

    private final Map<String, LanguageService> services;

    public LanguageRegistry(Collection<LanguageService> services) {
        this.services =
                services.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        service -> normalize(service.language()),
                                        Function.identity()));
    }

    public LanguageService get(String language) {
        LanguageService service = language == null ? null : services.get(normalize(language));
        if (service == null) {
            throw new UnsupportedLanguageException(language, null);
        }
        return service;
    }

    public LanguageService get(String language, String version) {
        LanguageService service = get(language);
        if (!service.versions().contains(version)) {
            throw new UnsupportedLanguageException(language, version);
        }
        return service;
    }

    public List<LanguageService> all() {
        return List.copyOf(services.values());
    }

    private static String normalize(String language) {
        return language.toLowerCase(Locale.ROOT);
    }
}
