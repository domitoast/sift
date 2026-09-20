package dev.sift.fetch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Fetch job queries.
 */
public interface FetchJobRepository extends JpaRepository<FetchJob, Long> {
    boolean existsBySourceIdAndStatusIn(Long sourceId, Collection<FetchStatus> statuses);

    List<FetchJob> findBySourceIdOrderByCreatedAtDesc(Long sourceId, Limit limit);

    List<FetchJob> findByStatusInAndCreatedAtBefore(
            Collection<FetchStatus> statuses, Instant cutoff);
}
