package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummarizerRegistryTest {
    @Test
    @DisplayName("★ 依供應商挑出對應的實作")
    void forProvider_shouldReturnMatchingImplementation() {
        Summarizer fake = stub(LlmProvider.FAKE, "假的");
        Summarizer gemini = stub(LlmProvider.GEMINI, "真的");

        SummarizerRegistry registry = new SummarizerRegistry(List.of(fake, gemini));

        assertThat(registry.forProvider(LlmProvider.FAKE)).isSameAs(fake);
        assertThat(registry.forProvider(LlmProvider.GEMINI)).isSameAs(gemini);
    }

    @Test
    @DisplayName("★★ 沒註冊的供應商 → PERMANENT，而且訊息要說出有哪些可用")
    void forProvider_unavailable_shouldFailPermanently() {
        SummarizerRegistry registry =
                new SummarizerRegistry(List.of(stub(LlmProvider.GEMINI, "真的")));

        assertThatThrownBy(() -> registry.forProvider(LlmProvider.FAKE))
                .isInstanceOf(SummarizationException.class)
                .satisfies(e -> {
                    SummarizationException ex = (SummarizationException) e;

                    assertThat(ex.getFailureType()).isEqualTo(FailureType.PERMANENT);

                    assertThat(ex.getMessage()).contains("FAKE").contains("GEMINI");
                });
    }

    @Test
    @DisplayName("★ available() 回報這個環境支援哪些——前端的下拉選單用它")
    void available_shouldListRegisteredProviders() {
        SummarizerRegistry registry = new SummarizerRegistry(
                List.of(stub(LlmProvider.GEMINI, "真的")));

        assertThat(registry.available()).containsExactly(LlmProvider.GEMINI);
    }

    private Summarizer stub(LlmProvider provider, String summary) {
        return new Summarizer() {
            @Override
            public LlmProvider provider() {
                return provider;
            }

            @Override
            public String summarize(String title, String content, String apiKey) {
                return summary;
            }
        };
    }
}
