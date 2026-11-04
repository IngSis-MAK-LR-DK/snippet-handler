package edu.austral.ingsis.snippethandler.snippet;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnippetRepository
        extends JpaRepository<Snippet, UUID>, JpaSpecificationExecutor<Snippet> {

    @Query("select s.id from Snippet s where s.language = :language")
    List<UUID> findIdsByLanguage(@Param("language") String language);
}
