package dev.sift.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 的資料存取層。
 *
 * <p><b>注意這是 interface，不是 class，而且沒有任何實作。</b>
 * Spring Data JPA 會在啟動時掃到它，依方法名稱自動產生實作並註冊為 Bean。
 *
 * <p>繼承 {@code JpaRepository<User, Long>} 之後，直接獲得一整組現成方法：
 * <ul>
 *   <li>{@code save(user)} — 新增或更新</li>
 *   <li>{@code findById(id)} — 依主鍵查詢</li>
 *   <li>{@code findAll()} — 查全部（正式環境慎用）</li>
 *   <li>{@code delete(user)} — 實體刪除</li>
 *   <li>{@code count()} — 計數</li>
 * </ul>
 * 泛型的兩個參數分別是「Entity 類別」與「主鍵型別」。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 依 email 查詢未刪除的使用者。
     *
     * <p>方法名稱會被解析成：
     * {@code SELECT * FROM app_user WHERE email = ? AND deleted_at IS NULL}
     *
     * <p><b>為什麼一定要帶 {@code AndDeletedAtIsNull}</b>：
     * 我們採用 soft delete（ADR-005），已刪除的資料仍留在表中。
     * 若寫成 {@code findByEmail}，登入時會查到已刪除的帳號並允許其登入。
     *
     * <p>回傳 {@code Optional<User>} 而非 {@code User}：
     * 這是在型別上強迫呼叫者處理「查不到」的情況。
     * 若直接回傳 User，呼叫者很容易忘記檢查 null，
     * 直到某天在正式環境噴 NullPointerException。
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /**
     * 檢查該 email 是否已被使用（未刪除者）。
     *
     * <p>產生的 SQL 只做存在性檢查，不會把整筆資料撈回來，
     * 比 {@code findBy...().isPresent()} 省一次資料傳輸。
     *
     * <p>⚠️ 這個方法用於「給使用者友善的錯誤訊息」，
     * <b>不是</b> email 唯一性的保證。
     * 真正的保證是資料庫的 partial unique index——
     * 因為「先查再寫」之間有空隙，並發時兩個請求可能同時通過檢查
     * （race condition，Day 3 教過）。
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);
}
