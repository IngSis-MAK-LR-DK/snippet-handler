package edu.austral.ingsis.snippethandler.rules;

import java.util.List;
import java.util.UUID;

import edu.austral.ingsis.snippethandler.language.RuleType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleOverrideRepository extends JpaRepository<RuleOverride, UUID> {

    List<RuleOverride> findAllByUserIdAndType(String userId, RuleType type);
}
