package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定時觸發抓取。
 *
 * <p><b>這個類別只負責「什麼時候做」，不負責「做什麼」。</b>
 * 實際的抓取邏輯在 FetchService（下一步實作）。
 *
 * <p>這樣拆的好處：想手動觸發一次抓取時，直接呼叫 FetchService 就好，
 * 不需要等排程，也不需要為了測試而去操作時間。
 */
@Component
public class FetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(FetchScheduler.class);

    private final FetchService fetchService;

    public FetchScheduler(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    /**
     * 定時抓取。
     *
     * <p><b>用 fixedDelay 不用 fixedRate</b>：
     * 抓取要多久取決於別人的伺服器，可能 1 秒也可能 30 秒。
     * fixedRate 是「每 N 毫秒開始一次」，前一輪還沒跑完就又開一輪，
     * 慢的時候會愈積愈多。fixedDelay 是「上一輪結束後再等 N 毫秒」，
     * 永遠不會重疊。
     *
     * <p>{@code fixedDelayString} 讀設定檔（{@code fixedDelay} 只吃寫死的數字）。
     * 開發時設 60 秒方便觀察，正式環境應該是每小時或每天。
     */
    @Scheduled(
            fixedDelayString = "${sift.fetch.interval-ms}",
            initialDelayString = "${sift.fetch.initial-delay-ms}")
    public void fetchAll() {

        long startedAt = System.currentTimeMillis();

        fetchService.fetchAll();

        log.info("本輪耗時 {} ms", System.currentTimeMillis() - startedAt);
    }
}
