package edu.austral.ingsis.snippethandler.snippet;

import edu.austral.ingsis.snippethandler.language.LanguageService;
import edu.austral.ingsis.snippethandler.language.LanguageServiceException;
import edu.austral.ingsis.snippethandler.language.RuleType;
import edu.austral.ingsis.snippethandler.rules.RuleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Lints and formats snippet sources with the rules their owner has enabled. */
@Component
public class SnippetStyle {

    private static final Logger LOG = LoggerFactory.getLogger(SnippetStyle.class);

    private final RuleService rules;

    public SnippetStyle(RuleService rules) {
        this.rules = rules;
    }

    /** Never fails: a snippet that cannot be linted is reported as {@link Compliance#FAILED}. */
    public LintReport lint(
            String ownerId, LanguageService language, String version, String source) {
        try {
            return LintReport.of(
                    language.lint(
                            source, version, rules.enabledRules(ownerId, language, RuleType.LINT)));
        } catch (LanguageServiceException e) {
            LOG.warn("Could not lint a snippet of {}", ownerId, e);
            return LintReport.failed();
        }
    }

    public String format(String ownerId, LanguageService language, String version, String source) {
        return language.format(
                source, version, rules.enabledRules(ownerId, language, RuleType.FORMAT));
    }
}
