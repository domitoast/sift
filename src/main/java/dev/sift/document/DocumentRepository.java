package dev.sift.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Document 的資料存取層。
 *
 * <p><b>注意每一個方法名稱都帶著 {@code AndUserId}。</b>
 *
 * <p>這是刻意的：<b>權限檢查寫進查詢條件，而不是查出來之後再比對。</b>
 * 這裡沒有提供「只用 id 查」的方法，因此呼叫端不可能忘記帶擁有者。
 *
 * <p>對照組：若提供了 {@code findById(id)}，某天有人為了方便直接用它，
 * 就會產生一個可以讀到任何人資料的漏洞——而且程式碼看起來完全正常。
 *
 * <p><b>好的設計不是「記得做對的事」，是「做錯的事變得做不到」。</b>
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 查詢某使用者的單篇未刪除文件。
     *
     * <p>產生的 SQL：
     * <pre>
     * SELECT * FROM document
     * WHERE id = ? AND user_id = ? AND deleted_at IS NULL
     * </pre>
     *
     * <p>查別人的文件會得到空的 {@code Optional}——與「文件不存在」完全相同的結果。
     * 因此對外自然回應 <b>404 而非 403</b>，不洩漏「這個 id 存在但不屬於你」（ADR-006）。
     */
    Optional<Document> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /**
     * 分頁查詢某使用者的未刪除文件，<b>只取列表需要的欄位</b>。
     *
     * <p>回傳 {@link DocumentSummary}（projection）而非 {@code Document}，
     * 因此產生的 SQL 不含 {@code content}。
     *
     * <p><b>{@code Pageable} 參數是必要的，不是選配。</b>
     * 若寫成回傳 {@code List}，這支 API 在使用者有 10 篇文件時運作良好，
     * 有 10 萬篇時會把整張表載入記憶體——而且不會有任何警告。
     *
     * <p>回傳 {@code Page} 而非 {@code List}：Spring Data 會另外執行一次
     * {@code COUNT(*)}，讓前端知道總筆數與總頁數。
     * 代價是每次查詢變兩句；若日後只需要「有沒有下一頁」，可改用 {@code Slice}。
     *
     * <p>方法名稱中 {@code find} 與 {@code By} 之間的字（{@code Summaries}）
     * 只是給人看的，Spring Data 會忽略它。
     * 需要它的原因是：Java 不允許只靠回傳型別區分同名方法。
     */
    Page<DocumentSummary> findSummariesByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);
}
