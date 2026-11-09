package edu.austral.ingsis.snippethandler.reprocessing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReprocessTaskRepository extends JpaRepository<ReprocessTask, UUID> {

    List<ReprocessTask> findAllByJobIdAndStatus(UUID jobId, TaskStatus status);

    @Query(
            "select new edu.austral.ingsis.snippethandler.reprocessing.StatusCount("
                    + "t.status, count(t)) from ReprocessTask t "
                    + "where t.jobId = :jobId group by t.status")
    List<StatusCount> countByStatus(@Param("jobId") UUID jobId);
}
