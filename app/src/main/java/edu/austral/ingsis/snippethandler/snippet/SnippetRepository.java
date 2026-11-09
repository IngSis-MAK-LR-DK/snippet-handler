package edu.austral.ingsis.snippethandler.snippet;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnippetRepository
        extends JpaRepository<Snippet, UUID>, JpaSpecificationExecutor<Snippet> {

    @Query("select s.id from Snippet s where s.language = :language")
    List<UUID> findIdsByLanguage(@Param("language") String language);

    @Query("select s.id from Snippet s where s.ownerId = :ownerId and s.language = :language")
    List<UUID> findIdsByOwnerIdAndLanguage(
            @Param("ownerId") String ownerId, @Param("language") String language);

    /**
     * Bulk update: does not touch {@code updatedAt}. Pending writes are flushed first because
     * clearing the context would otherwise drop the ones on other tables.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update Snippet s set s.compliance = :compliance "
                    + "where s.ownerId = :ownerId and s.language = :language")
    int markCompliance(
            @Param("ownerId") String ownerId,
            @Param("language") String language,
            @Param("compliance") Compliance compliance);
}
