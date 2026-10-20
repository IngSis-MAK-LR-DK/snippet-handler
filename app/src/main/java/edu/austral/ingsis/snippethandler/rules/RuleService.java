package edu.austral.ingsis.snippethandler.rules;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.RuleCatalog;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Lint and format rules per user. Rules are the defaults offered by the language; only the changes
 * a user makes over them are stored.
 */
@Service
public class RuleService {

    private final RuleOverrideRepository repository;
    private final LanguageRegistry languages;
    private final TransactionOperations transaction;
    private final String defaultLanguage;

    public RuleService(
            RuleOverrideRepository repository,
            LanguageRegistry languages,
            TransactionOperations transaction,
            SnippetProperties properties) {
        this.repository = repository;
        this.languages = languages;
        this.transaction = transaction;
        this.defaultLanguage = properties.defaultLanguage();
    }

    /**
     * @param language language whose rules are listed; the default language if null
     */
    public List<RuleView> rules(String userId, String language, RuleType type) {
        return views(resolve(language).defaultRules(type), overrides(userId, type));
    }

    /** Enabled rules of a user for the given language, ready to be sent to it. */
    public List<RuleConfig> enabledRules(String userId, RuleCatalog language, RuleType type) {
        return views(language.defaultRules(type), overrides(userId, type)).stream()
                .filter(RuleView::enabled)
                .map(rule -> new RuleConfig(rule.name(), true, rule.value()))
                .toList();
    }

    /** All the changes are applied or none is. */
    public List<RuleView> update(
            String userId, String language, RuleType type, List<RuleConfig> changes) {
        // The language adapter is asked before the transaction starts.
        List<RuleDefinition> definitions = resolve(language).defaultRules(type);
        Map<String, RuleDefinition> known =
                definitions.stream()
                        .collect(Collectors.toMap(RuleDefinition::name, Function.identity()));
        for (RuleConfig change : changes) {
            if (!known.containsKey(change.name())) {
                throw new BadRequestException(
                        "Unknown %s rule '%s'".formatted(type, change.name()));
            }
        }
        Map<String, RuleOverride> updated =
                transaction.execute(
                        status -> {
                            Map<String, RuleOverride> overrides = overrides(userId, type);
                            for (RuleConfig change : changes) {
                                RuleOverride override =
                                        overrides.computeIfAbsent(
                                                change.name(),
                                                name -> new RuleOverride(userId, type, name));
                                override.configure(change.enabled(), change.value());
                                repository.save(override);
                            }
                            return overrides;
                        });
        return views(definitions, updated);
    }

    private static List<RuleView> views(
            List<RuleDefinition> definitions, Map<String, RuleOverride> overrides) {
        return definitions.stream()
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

    private RuleCatalog resolve(String language) {
        return languages.get(language == null ? defaultLanguage : language);
    }
}
