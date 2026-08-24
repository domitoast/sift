package dev.sift.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * RefreshToken 的資料存取層。
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 依目前的雜湊值查詢——正常換發走這條路。
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 依「上一代」的雜湊值查詢——盜用偵測走這條路。
     *
     * <p>查得到代表：有人拿著一張已經被換掉的票來換發。
     */
    Optional<RefreshToken> findByPreviousTokenHash(String previousTokenHash);

    /**
     * 查出某使用者的所有 refresh token（含已撤銷、已過期）。
     *
     * <p>目前只有測試在用。未來若要做「檢視我登入中的裝置」，
     * 這個方法會是起點。
     */
    List<RefreshToken> findAllByUserId(Long userId);

    /**
     * 作廢某使用者所有尚未撤銷的 refresh token。
     *
     * <p>偵測到盜用時使用——因為伺服器分不清兩個持有者裡哪一個是本人，
     * 只能全部作廢，讓雙方都重新登入。
     *
     * <p><b>{@code @Modifying}</b>：告訴 Spring Data 這是寫入操作而非查詢。
     * 少了它，Spring 會試圖把 UPDATE 當成 SELECT 執行並拋出例外。
     *
     * <p><b>{@code @Query}</b>：這裡無法用方法命名規則表達
     * 「批次更新」，所以自己寫查詢。
     * 語法是 JPQL——操作的是 <b>Entity 與欄位名稱</b>（{@code RefreshToken}、
     * {@code userId}），不是資料表與欄位名（{@code refresh_token}、{@code user_id}）。
     *
     * <p><b>為什麼用批次 UPDATE 而不是「撈出來、逐一 revoke()、再存回去」</b>：
     * 後者是 N+1 次資料庫往返，前者只有一次。
     * 一個使用者可能有十幾個裝置的 session，差距是十幾倍。
     *
     * <p>⚠️ 代價：批次 UPDATE <b>繞過 Hibernate 的一級快取</b>。
     * 若同一個交易裡已經載入過這些物件，記憶體中的它們不會反映這次更新。
     * 本專案的用法是「偵測到盜用 → 全部作廢 → 立刻回 401」，
     * 之後不再使用那些物件，因此安全。
     *
     * @return 實際被更新的列數
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now " +
           "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
