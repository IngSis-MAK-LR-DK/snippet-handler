package edu.austral.ingsis.snippethandler.rules;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lint and format rules per user. Rules are the defaults offered by the language; only the changes
 * a user makes over them are stored.
 */
@Service
public class RuleService {

    private final RuleOverrideRepository repository;
    private final LanguageRegistry languages;
    private final String defaultLanguage;

    public RuleService(
            RuleOverrideRepository repository,
            LanguageRegistry languages,
            SnippetProperties properties) {
        this.repository = repository;
        this.languages = languages;
        this.defaultLanguage = properties.defaultLanguage();
    }

    /**
     * @param language language whose rules are listed; the default language if null
     */
    @Transactional(readOnly = true)
    public List<RuleView> rules(String userId, String language, RuleType type) {
        return rules(userId, resolve(language), type);
    }

    /** Enabled rules of a user for the given language, ready to be sent to it. */
    @Transactional(readOnly = true)
    public List<RuleConfig> enabledRules(String userId, LanguageService language, RuleType type) {
        return rules(userId, language, type).stream()
                .filter(RuleView::enabled)
                .map(rule -> new RuleConfig(rule.name(), true, rule.value()))
                .toList();
    }

    @Transactional
    public List<RuleView> update(
            String userId, String language, RuleType type, List<RuleConfig> changes) {
        LanguageService service = resolve(language);
        Set<String> known =
                service.defaultRules(type).stream()
                        .map(RuleDefinition::name)
                        .collect(Collectors.toSet());
        Map<String, RuleOverride> overrides = overrides(userId, type);
        for (RuleConfig change : changes) {
            if (!known.contains(change.name())) {
                throw new BadRequestException(
                        "Unknown %s rule '%s'".formatted(type, change.name()));
            }
            RuleOverride override =
                    overrides.computeIfAbsent(
                            change.name(), name -> new RuleOverride(userId, type, name));
            override.configure(change.enabled(), change.value());
            repository.save(override);
        }
        return rules(userId, service, type);
    }

    private List<RuleView> rules(String userId, LanguageService language, RuleType type) {
        Map<String, RuleOverride> overrides = overrides(userId, type);
        return language.defaultRules(type).stream()
                .map(definition -> view(definition, overrides.get(definition.name())))
                .toList();
    }

    private static RuleView view(RuleDefinition definition, RuleOverride override) {
        if (override == null) {
            return new RuleView(
                    definition.name(),
                    definition.description(),
                    definition.enabledByDefault(),
                    definition.defaultValue());
        }
        return new RuleView(
                definition.name(),
                definition.description(),
                override.isEnabled(),
                override.getValue() == null ? definition.defaultValue() : override.getValue());
    }

    private Map<String, RuleOverride> overrides(String userId, RuleType type) {
        return repository.findAllByUserIdAndType(userId, type).stream()
                .collect(Collectors.toMap(RuleOverride::getName, Function.identity()));
    }

    private LanguageService resolve(String language) {
        return languages.get(language == null ? defaultLanguage : language);
    }
}
