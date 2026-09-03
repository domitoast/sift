package dev.sift.summarize;

/**
 * 產生一篇文章的摘要。
 *
 * <h2>為什麼要是介面</h2>
 *
 * 呼叫 LLM 是「我們控制不了」的外部依賴：會失敗、會限流、會花錢、會慢。
 * 把它抽成介面之後：
 *
 * <ul>
 *   <li><b>測試不需要 API key</b>——不然每跑一次 {@code mvnw test} 都在花錢，
 *       而且結果會隨模型的回答改變，斷言寫不出來</li>
 *   <li><b>換供應商只是換一個實作</b>——Anthropic、Gemini、本地模型都可以</li>
 * </ul>
 *
 * <p>這與 Day 16 學的「只 mock 你控制不了的東西」是同一個判斷，
 * 只是這次從一開始就設計成可替換，不必等到寫測試才用 mock 硬套。
 */
public interface Summarizer {

    /**
     * @param title   文章標題
     * @param content 文章內文，<b>可能是 null</b>——很多 feed 只給標題
     * @param apiKey  這個使用者自己的 API key（ADR-003 BYOK）。
     *                <b>實作絕對不可以把它寫進日誌或例外訊息</b>
     * @return 摘要文字，不會是 null 或空字串
     * @throws SummarizationException 產生摘要失敗，並附上該不該重試的分類
     */
    String summarize(String title, String content, String apiKey);
}
