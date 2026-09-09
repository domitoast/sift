package dev.sift.summarize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每日 LLM 呼叫的上限。
 *
 * <h2>這是安全閥，不是商業規則</h2>
 *
 * ADR-003 明確定位：配額的角色是<b>「防止爆量」</b>，不是「限制使用者」。
 *
 * <p>因為採 BYOK，花的是使用者自己的錢——我們沒有立場限制他用多少。
 * 但我們有責任確保<b>我們的程式不會因為一個 bug 而幫他把錢燒光</b>。
 *
 * <p>具體的失控情境：有人把排程間隔從 30 秒改成 1 秒，
 * 一天就是 86 萬次呼叫。上限 200 的意思是「出事的時候損失有天花板」。
 *
 * <h2>為什麼「先加再打」而不是「打完再加」</h2>
 *
 * 因為我們要限制的是<b>嘗試次數</b>。
 * 呼叫送出去之後就可能被計費了，即使它最後失敗。
 * 打完才加的話，一直失敗的呼叫永遠不計數——正好是最會燒錢的情況。
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final LlmUsageRepository llmUsageRepository;
    private final int dailyLimit;

    public QuotaService(LlmUsageRepository llmUsageRepository,
                        @Value("${sift.summarize.daily-quota}") int dailyLimit) {
        this.llmUsageRepository = llmUsageRepository;
        this.dailyLimit = dailyLimit;
    }

    /**
     * 檢查配額並佔用一次額度。
     *
     * @return true 代表可以呼叫；false 代表今天的額度用完了
     */
    @Transactional
    public boolean tryConsume(Long userId) {

        int used = llmUsageRepository.countToday(userId);

        if (used >= dailyLimit) {
            /*
             * 用 warn 不是 debug：額度用完是「需要有人知道」的事。
             *
             * 正常情況一天不會用到 200 次。真的撞到上限，
             * 要嘛是使用者訂了非常多來源，要嘛是我們的程式出問題了。
             * 兩種都值得看一眼。
             */
            log.warn("今日 LLM 配額已用完 userId={} 已用={} 上限={}", userId, used, dailyLimit);
            return false;
        }

        llmUsageRepository.increment(userId);
        return true;
    }
}
