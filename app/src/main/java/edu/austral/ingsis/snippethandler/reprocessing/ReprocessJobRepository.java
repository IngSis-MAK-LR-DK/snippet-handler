package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReprocessJobRepository extends JpaRepository<ReprocessJob, UUID> {

    List<ReprocessJob> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
