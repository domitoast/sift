package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 假的摘要產生器：不呼叫任何外部 API，直接用標題與內文組出一段文字。
 *
 * <p><b>存在的理由</b>：
 *
 * <ul>
 *   <li>測試不需要 API key，也不會因為外部服務掛掉而變紅</li>
 *   <li>整條管線可以先跑通，真的接 API 只是換一個實作</li>
 * </ul>
 *
 * <p><b>輸出刻意加上 {@code [FAKE]} 前綴</b>——
 * 沒有它，資料庫裡的假摘要看起來就像真的，
 * 有人會拿去做 demo，然後在某個場合被問「這是模型寫的嗎」。
 *
 * <p>⚠️ Day 19 加上真的實作之後，這個類別要改成只在測試環境啟用。
 */
@Component
public class FakeSummarizer implements Summarizer {

    private static final Logger log = LoggerFactory.getLogger(FakeSummarizer.class);

    private static final int MAX_LENGTH = 100;

    @Override
    public String summarize(String title, String content, String apiKey) {

        /*
         * 即使是假的實作，也要驗證 apiKey 有沒有給。
         *
         * 理由：如果假的實作比真的寬鬆，測試就測不到「沒有 key 會怎樣」，
         * 而那個情況在正式環境一定會發生。
         *
         * 假的東西行為要跟真的一致，否則它提供的是假的信心。
         */
        if (apiKey == null || apiKey.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "沒有可用的 API key");
        }

        if (title == null || title.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "文章沒有標題");
        }

        // ⚠️ 只記 id 層級的資訊，不記 apiKey，也不記它的長度或前綴
        log.debug("產生假摘要 title={}", title);

        String source = (content == null || content.isBlank()) ? title : content;

        String trimmed = source.length() <= MAX_LENGTH
                ? source
                : source.substring(0, MAX_LENGTH) + "…";

        return "[FAKE] " + trimmed;
    }
}
