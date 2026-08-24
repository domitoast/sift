package dev.sift.document;

import java.time.Instant;

/**
 * 列表查詢用的 projection——只取得需要的欄位，不載入整個 Document。
 *
 * <p><b>它是 interface，沒有實作。</b>
 * Spring Data 看到 Repository 方法的回傳型別是這個介面，
 * 就只會 {@code SELECT} 這裡宣告的欄位，並在執行時產生一個代理物件。
 *
 * <p><b>解決的問題</b>：原本列表查詢載入完整的 Document（含 {@code content}），
 * 再於 Java 端轉成精簡 DTO 時丟棄。
 * HTTP 回應確實變小了，但「資料庫 → 應用程式」那一段完全沒省到。
 *
 * <p>每篇 50 KB 的內文、一頁 20 筆的話，就是每次列表白拉 1 MB。
 *
 * <p><b>getter 的名稱必須對應 entity 的屬性名</b>（{@code getTitle} → {@code title}）。
 * 打錯字會在啟動時失敗，不會等到執行期。
 */
public interface DocumentSummary {

    Long getId();

    String getTitle();

    DocumentOrigin getOrigin();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
