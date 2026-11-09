package edu.austral.ingsis.snippethandler.rules;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.austral.ingsis.snippethandler.config.SnippetProperties;
import edu.austral.ingsis.snippethandler.error.BadRequestException;
import edu.austral.ingsis.snippethandler.language.LanguageRegistry;
import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.RuleCatalog;
import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleDefinition;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.reprocessing.RulesChangedPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Lint and format rules per user. Rules are the defaults offered by the language; only the changes
 * a user makes over them are stored.
 */
@Service
public class RuleService {

    private static final Logger LOG = LoggerFactory.getLogger(RuleService.class);

    private final RuleOverrideRepository repository;
    private final LanguageRegistry languages;
    private final TransactionOperations transaction;
    private final RulesChangedPublisher publisher;
    private final String defaultLanguage;

    public RuleService(
            RuleOverrideRepository repository,
            LanguageRegistry languages,
            TransactionOperations transaction,
            RulesChangedPublisher publisher,
            SnippetProperties properties) {
        this.repository = repository;
        this.languages = languages;
        this.transaction = transaction;
        this.publisher = publisher;
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
        LanguageService resolved = resolve(language);
        List<RuleDefinition> definitions = resolved.defaultRules(type);
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
        publishChange(userId, type, resolved.language(), changes.size());
        return views(definitions, updated);
    }

    /**
     * US 12 and 15: the stored snippets are reprocessed in the background. The rules are already
     * saved, so a broker that is down does not undo or fail the update.
     */
    private void publishChange(String userId, RuleType type, String language, int applied) {
        if (applied == 0) {
            return;
        }
        try {
            publisher.rulesChanged(userId, type, language);
        } catch (RuntimeException e) {
            LOG.error("Could not announce the {} rules change of {}", type, userId, e);
        }
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

    private LanguageService resolve(String language) {
        return languages.get(language == null ? defaultLanguage : language);
    }
}
