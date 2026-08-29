package dev.sift.fetch;

/**
 * 一次抓取任務的狀態。
 *
 * <p>合法的轉換只有兩條路：
 *
 * <pre>
 * PENDING ──start()──> RUNNING ──succeed()──> SUCCESS
 *                              └──fail()────> FAILED
 * </pre>
 *
 * <p><b>SUCCESS 與 FAILED 都是終點，出去了就回不來。</b>
 * 失敗不重跑——等下次排程開一筆全新的 fetch_job（ADR-008）。
 *
 * <p>這組值與資料庫的 {@code ck_fetch_job_status} 約束一致。
 * 兩邊都要改才算改完。
 */
public enum FetchStatus {

    /** 已建立，還沒開始抓。 */
    PENDING,

    /** 正在抓。 */
    RUNNING,

    /** 抓完了。 */
    SUCCESS,

    /** 失敗了。原因記在 failureType 與 failureReason。 */
    FAILED
}
