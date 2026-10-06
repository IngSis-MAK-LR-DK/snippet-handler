package edu.austral.ingsis.snippethandler.language;

import java.util.List;

public record LanguageInfo(String name, List<String> versions, String fileExtension) {

    public static LanguageInfo of(LanguageService language) {
        return new LanguageInfo(
                language.language(),
                language.versions().stream().sorted().toList(),
                language.fileExtension());
    }
}
