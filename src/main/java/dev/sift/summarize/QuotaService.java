package dev.sift.summarize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the daily per-user call limit.
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

    @Transactional
    public boolean tryConsume(Long userId) {
        int used = llmUsageRepository.countToday(userId);

        if (used >= dailyLimit) {
            log.warn("今日 LLM 配額已用完 userId={} 已用={} 上限={}", userId, used, dailyLimit);
            return false;
        }

        llmUsageRepository.increment(userId);
        return true;
    }
}
