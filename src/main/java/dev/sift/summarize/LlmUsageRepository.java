package dev.sift.summarize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Quota counter storage. Increments use an upsert to stay correct under concurrency.
 */
public interface LlmUsageRepository extends JpaRepository<LlmUsage, LlmUsageId> {
    @Query(value = """
            SELECT COALESCE(
                (SELECT call_count FROM llm_usage
                 WHERE user_id = :userId AND usage_date = CURRENT_DATE),
                0)
            """, nativeQuery = true)
    int countToday(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO llm_usage (user_id, usage_date, call_count)
            VALUES (:userId, CURRENT_DATE, 1)
            ON CONFLICT (user_id, usage_date)
            DO UPDATE SET call_count = llm_usage.call_count + 1
            """, nativeQuery = true)
    void increment(@Param("userId") Long userId);
}
