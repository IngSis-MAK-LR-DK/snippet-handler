package edu.austral.ingsis.snippethandler.rules;

import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A user's change over the default configuration of a rule. */
@Entity
@Table(name = "rule_config")
public class RuleOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    private RuleType type;

    private String name;

    private boolean enabled;

    @Column(name = "rule_value")
    private String value;

    protected RuleOverride() {}

    public RuleOverride(String userId, RuleType type, String name) {
        this.userId = userId;
        this.type = type;
        this.name = name;
    }

    public void configure(boolean enabled, String value) {
        this.enabled = enabled;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getValue() {
        return value;
    }
}
