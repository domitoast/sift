package dev.sift.fetch.dto;

import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemStatus;

import java.time.Instant;

/**
 * 一篇抓下來的文章，回給使用者看。
 *
 * <h2>刻意不包含的欄位</h2>
 *
 * <ul>
 *   <li><b>{@code rawContent}</b> — 原始內文可能好幾 KB。列表回傳 20 篇就是
 *       好幾十 KB 的傳輸，而使用者在列表上根本看不到那些內容。
 *       想看全文的話點進去看原文（{@code externalUrl}）。
 *       <br>與 {@code DocumentSummary} 是同一個判斷（Day 9）</li>
 *   <li><b>{@code sourceId}</b> — 已經在網址裡了
 *       （{@code /sources/&#123;id&#125;/items}），重複回傳沒有意義</li>
 *   <li><b>{@code contentHash}</b> — 內部去重用的，對使用者沒有意義</li>
 *   <li><b>{@code retryCount}、{@code nextRetryAt}</b> — 同上，內部排程用</li>
 * </ul>
 *
 * <h2>為什麼包含 status 與 failureReason</h2>
 *
 * <b>API 不該替前端決定「什麼該被看到」，除非有安全理由。</b>
 *
 * <p>摘要失敗的文章仍然回傳，帶著 {@code status = FAILED}。
 * 前端要灰掉、摺疊、還是排到最下面，那是前端的判斷，而且會改。
 * 若 API 直接濾掉，前端連「有這回事」都不知道。
 *
 * <p>反過來說：使用者抓了 30 篇卻只看到 25 篇，他不會知道另外 5 篇去哪了，
 * 只會覺得「這個訂閱怪怪的」。
 *
 * @param status  NEW（還沒摘要）/ SUMMARIZING / READY / FAILED
 * @param summary LLM 產生的摘要。<b>只有 READY 狀態才會有值</b>
 */
public record FetchedItemResponse(
        Long id,
        String title,
        String summary,
        FetchedItemStatus status,
        String externalUrl,
        Instant publishedAt,
        String failureReason
) {

    public static FetchedItemResponse from(FetchedItem item) {
        return new FetchedItemResponse(
                item.getId(),
                item.getTitle(),
                item.getSummary(),
                item.getStatus(),
                item.getExternalUrl(),
                item.getPublishedAt(),
                item.getFailureReason()
        );
    }
}
