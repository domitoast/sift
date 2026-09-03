package dev.sift.summarize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定時把待處理的文章送去產生摘要。
 *
 * <p>與 {@code FetchScheduler} 分開的理由：<b>兩件事的節奏不同。</b>
 *
 * <table border="1">
 *   <tr><td>抓取</td><td>每小時一次就夠——RSS 不會每分鐘更新</td></tr>
 *   <tr><td>摘要</td><td>抓到之後應該盡快處理，使用者才看得到結果</td></tr>
 * </table>
 *
 * <p>寫在同一個排程裡的話，兩者就被綁死成同一個頻率。
 */
@Component
public class SummarizeScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummarizeScheduler.class);

    private final SummarizeService summarizeService;

    public SummarizeScheduler(SummarizeService summarizeService) {
        this.summarizeService = summarizeService;
    }

    @Scheduled(
            fixedDelayString = "${sift.summarize.interval-ms}",
            initialDelayString = "${sift.summarize.initial-delay-ms}")
    public void summarize() {

        try {
            summarizeService.summarizePending();

        } catch (Exception e) {
            /*
             * 排程方法丟出例外會怎樣：Spring 記一行 error，然後「照常安排下一次」。
             * 不會停掉整個排程。
             *
             * 但預設的錯誤訊息很難懂，所以自己接住並記清楚。
             */
            log.error("摘要排程發生預期外的錯誤", e);
        }
    }
}
