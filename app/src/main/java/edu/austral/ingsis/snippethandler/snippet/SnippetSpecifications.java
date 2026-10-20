package edu.austral.ingsis.snippethandler.snippet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

final class SnippetSpecifications {

    private SnippetSpecifications() {}

    /** Snippets among {@code ids} matching every non-null criterion of {@code filter}. */
    static Specification<Snippet> matching(Collection<UUID> ids, SnippetFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("id").in(ids));
            if (filter.name() != null && !filter.name().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + filter.name().toLowerCase(Locale.ROOT) + "%"));
            }
            if (filter.language() != null && !filter.language().isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("language")),
                                filter.language().toLowerCase(Locale.ROOT)));
            }
            if (filter.compliance() != null) {
                predicates.add(cb.equal(root.get("compliance"), filter.compliance()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
