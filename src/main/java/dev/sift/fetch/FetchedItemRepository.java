package dev.sift.fetch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Fetched article queries, including the cross-table lookup that skips users
 * without an API key so they cannot block the queue.
 */
public interface FetchedItemRepository extends JpaRepository<FetchedItem, Long> {
    List<FetchedItem> findByStatusOrderByCreatedAtAsc(FetchedItemStatus status, Limit limit);

    List<FetchedItem> findBySourceIdOrderByCreatedAtDescIdDesc(Long sourceId, Limit limit);

    @Query(value = """
            SELECT fi.* FROM fetched_item fi
            JOIN source   s ON s.id = fi.source_id
            JOIN app_user u ON u.id = s.user_id
            WHERE fi.status = :status
              AND s.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.llm_api_key_encrypted IS NOT NULL
              AND (fi.next_retry_at IS NULL OR fi.next_retry_at <= now())
            ORDER BY fi.created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<FetchedItem> findProcessable(@Param("status") String status, @Param("limit") int limit);

    @Query(value = """
            SELECT fi.source_id                                      AS sourceId,
                   count(*)                                          AS total,
                   count(*) FILTER (WHERE fi.status = 'READY')       AS ready
            FROM fetched_item fi
            JOIN source s ON s.id = fi.source_id
            WHERE s.user_id = :userId
              AND s.deleted_at IS NULL
            GROUP BY fi.source_id
            """, nativeQuery = true)
    List<SourceItemCount> countByUser(@Param("userId") Long userId);

    List<FetchedItem> findByStatusAndUpdatedAtBefore(FetchedItemStatus status, Instant cutoff);

    long countBySourceId(Long sourceId);
}
