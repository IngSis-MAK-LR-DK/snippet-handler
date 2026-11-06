package edu.austral.ingsis.snippethandler.revalidation;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevalidationRunRepository extends JpaRepository<RevalidationRun, UUID> {}
