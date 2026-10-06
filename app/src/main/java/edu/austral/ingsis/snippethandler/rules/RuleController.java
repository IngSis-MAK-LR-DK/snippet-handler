package edu.austral.ingsis.snippethandler.rules;

import java.util.List;

import edu.austral.ingsis.snippethandler.language.RuleConfig;
import edu.austral.ingsis.snippethandler.language.RuleType;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lint (US 14) and format (US 11) rules of the current user. */
@RestController
@RequestMapping("/rules")
public class RuleController {

    private final RuleService rules;

    public RuleController(RuleService rules) {
        this.rules = rules;
    }

    @GetMapping("/lint")
    public List<RuleView> lintRules(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String language) {
        return rules.rules(jwt.getSubject(), language, RuleType.LINT);
    }

    @PutMapping("/lint")
    public List<RuleView> updateLintRules(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String language,
            @RequestBody List<RuleConfig> changes) {
        return rules.update(jwt.getSubject(), language, RuleType.LINT, changes);
    }

    @GetMapping("/format")
    public List<RuleView> formatRules(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String language) {
        return rules.rules(jwt.getSubject(), language, RuleType.FORMAT);
    }

    @PutMapping("/format")
    public List<RuleView> updateFormatRules(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String language,
            @RequestBody List<RuleConfig> changes) {
        return rules.update(jwt.getSubject(), language, RuleType.FORMAT, changes);
    }
}
