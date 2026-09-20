package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Summarizer that just truncates the input, for local development and tests.
 * Registered only when explicitly enabled, so production cannot reach it.
 */
@Component
@ConditionalOnProperty(name = "sift.summarize.allow-fake", havingValue = "true")
public class FakeSummarizer implements Summarizer {
    private static final Logger log = LoggerFactory.getLogger(FakeSummarizer.class);

    private static final int MAX_LENGTH = 100;

    public FakeSummarizer() {
        log.warn("⚠️ 使用 FakeSummarizer——摘要是假的，不會呼叫任何 LLM。僅限 test / dev 環境。");
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.FAKE;
    }

    @Override
    public String summarize(String title, String content, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "沒有可用的 API key");
        }

        if (title == null || title.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "文章沒有標題");
        }

        log.debug("產生假摘要 title={}", title);

        String source = (content == null || content.isBlank()) ? title : content;

        String trimmed = source.length() <= MAX_LENGTH
                ? source
                : source.substring(0, MAX_LENGTH) + "…";

        return "[FAKE] " + trimmed;
    }
}
