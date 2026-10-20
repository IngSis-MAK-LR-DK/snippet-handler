package edu.austral.ingsis.snippethandler.snippet;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SnippetRepository
        extends JpaRepository<Snippet, UUID>, JpaSpecificationExecutor<Snippet> {}
