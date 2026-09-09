package dev.sift.summarize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 每日 LLM 呼叫量。
 *
 * <p>這個 repository 只有兩個方法：讀今天用了幾次、把今天加一。
 * <b>刻意不繼承任何「查全部」的用法</b>——這張表只服務配額檢查。
 */
public interface LlmUsageRepository extends JpaRepository<LlmUsage, LlmUsageId> {

    /**
     * 今天用了幾次。沒有紀錄時回傳 0。
     *
     * <p>用原生 SQL 是為了 {@code CURRENT_DATE}——
     * 「今天」由資料庫決定，而不是由應用程式的時區決定。
     * 多台機器時區設定不一致的話，前者才會一致。
     */
    @Query(value = """
            SELECT COALESCE(
                (SELECT call_count FROM llm_usage
                 WHERE user_id = :userId AND usage_date = CURRENT_DATE),
                0)
            """, nativeQuery = true)
    int countToday(@Param("userId") Long userId);

    /**
     * 今天的計數加一。沒有紀錄就建一筆。
     *
     * <h2>UPSERT：一句 SQL 完成「有就更新，沒有就新增」</h2>
     *
     * <pre>{@code
     * INSERT ... VALUES (..., 1)
     * ON CONFLICT (user_id, usage_date)     ← 撞到主鍵的話
     * DO UPDATE SET call_count = llm_usage.call_count + 1
     * }</pre>
     *
     * <p><b>為什麼不寫成「先查，有就 UPDATE，沒有就 INSERT」</b>：
     * 那是三個動作，中間有兩個空隙。兩個執行緒同時查到「沒有」，
     * 就會雙雙 INSERT，第二個撞主鍵爆掉。
     *
     * <p>UPSERT 是<b>資料庫內部的單一動作</b>，沒有那個空隙。
     *
     * <p>這與 Day 17 的 insert-or-ignore 是同一類手法：
     * 把「檢查 + 動作」交給資料庫一次做完，而不是自己在應用層拼湊。
     *
     * <p>⚠️ {@code ON CONFLICT} 是 PostgreSQL 的語法。
     * MySQL 寫作 {@code ON DUPLICATE KEY UPDATE}。這是綁定資料庫的代價。
     */
    @Modifying
    @Query(value = """
            INSERT INTO llm_usage (user_id, usage_date, call_count)
            VALUES (:userId, CURRENT_DATE, 1)
            ON CONFLICT (user_id, usage_date)
            DO UPDATE SET call_count = llm_usage.call_count + 1
            """, nativeQuery = true)
    void increment(@Param("userId") Long userId);
}
