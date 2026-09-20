package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeSummarizerTest {
    private final Summarizer summarizer = new FakeSummarizer();

    private static final String KEY = "sk-ant-api03-abcdefghijklmnop";

    @Test
    @DisplayName("★ 宣告自己是 FAKE")
    void provider_shouldBeFake() {
        assertThat(summarizer.provider()).isEqualTo(LlmProvider.FAKE);
    }

    @Test
    @DisplayName("有內文時，用內文產生摘要")
    void summarize_withContent_shouldUseContent() {
        String summary = summarizer.summarize("標題", "這是文章的內文", KEY);

        assertThat(summary).contains("這是文章的內文");
    }

    @Test
    @DisplayName("★ 輸出帶 [FAKE] 前綴，不會被誤認成真的摘要")
    void summarize_shouldBeClearlyMarked() {
        assertThat(summarizer.summarize("標題", "內文", KEY)).startsWith("[FAKE]");
    }

    @Test
    @DisplayName("沒有內文時，退而用標題")
    void summarize_withoutContent_shouldUseTitle() {
        assertThat(summarizer.summarize("只有標題的文章", null, KEY))
                .contains("只有標題的文章");
    }

    @Test
    @DisplayName("很長的內文會被截斷")
    void summarize_longContent_shouldTruncate() {
        String summary = summarizer.summarize("標題", "字".repeat(500), KEY);

        assertThat(summary).endsWith("…");
        assertThat(summary.length()).isLessThan(200);
    }

    @Test
    @DisplayName("★★ 沒有 API key → PERMANENT 失敗")
    void summarize_withoutApiKey_shouldThrow() {
        assertThatThrownBy(() -> summarizer.summarize("標題", "內文", null))
                .isInstanceOf(SummarizationException.class)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }

    @Test
    @DisplayName("★ 沒有標題 → PERMANENT 失敗")
    void summarize_withoutTitle_shouldThrow() {
        assertThatThrownBy(() -> summarizer.summarize("  ", "內文", KEY))
                .isInstanceOf(SummarizationException.class)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }
}
