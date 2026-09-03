package dev.sift.fetch;

/**
 * 一篇抓下來的文章，在管線裡走到哪一步。
 *
 * <pre>
 * NEW ──> SUMMARIZING ──> READY ──> PROMOTED
 *              │            └──> DISCARDED（使用者看過，決定不要）
 *              └──> FAILED（摘要失敗，可重試）
 * </pre>
 *
 * <p>Day 17 只會用到 {@code NEW}——抓下來就停在這裡。
 * 後面的狀態要等 Day 18 的 LLM 摘要才會用到。
 *
 * <p>這組值與資料庫的 {@code ck_fetched_item_status} 約束一致。
 */
public enum FetchedItemStatus {

    /** 剛抓下來，還沒處理。 */
    NEW,

    /** 正在請 LLM 產生摘要。 */
    SUMMARIZING,

    /** 摘要好了，等使用者決定要不要收進知識庫。 */
    READY,

    /** 使用者決定收下，已經變成一篇 Document。 */
    PROMOTED,

    /** 摘要失敗。 */
    FAILED,

    /** 使用者看過，決定不要。 */
    DISCARDED
}
