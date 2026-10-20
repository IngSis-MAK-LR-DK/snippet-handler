package edu.austral.ingsis.snippethandler.languagecatalog;

import java.util.Comparator;
import java.util.List;

import edu.austral.ingsis.snippethandler.language.LanguageRegistry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Languages and versions a snippet can be written in. */
@RestController
@RequestMapping("/languages")
public class LanguageController {

    private final LanguageRegistry languages;

    public LanguageController(LanguageRegistry languages) {
        this.languages = languages;
    }

    @GetMapping
    public List<LanguageInfo> list() {
        return languages.all().stream()
                .map(LanguageInfo::of)
                .sorted(Comparator.comparing(LanguageInfo::name))
                .toList();
    }
}
