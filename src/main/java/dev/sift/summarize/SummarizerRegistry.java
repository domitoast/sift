package dev.sift.summarize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks a summarizer by provider.
 *
 * Built by injecting every Summarizer bean, so a new provider becomes available
 * by adding a class rather than by editing a switch or a config file.
 */
@Service
public class SummarizerRegistry {
    private static final Logger log = LoggerFactory.getLogger(SummarizerRegistry.class);

    private final Map<LlmProvider, Summarizer> byProvider;

    public SummarizerRegistry(List<Summarizer> summarizers) {
        this.byProvider = summarizers.stream()
                .collect(Collectors.toMap(Summarizer::provider, Function.identity()));

        log.info("可用的摘要供應商：{}", byProvider.keySet());
    }

    public Summarizer forProvider(LlmProvider provider) {
        Summarizer summarizer = byProvider.get(provider);

        if (summarizer == null) {
            throw new SummarizationException(
                    dev.sift.fetch.FailureType.PERMANENT,
                    "不支援的 LLM 供應商：%s（目前可用：%s）"
                            .formatted(provider, byProvider.keySet()));
        }

        return summarizer;
    }

    public List<LlmProvider> available() {
        return List.copyOf(byProvider.keySet());
    }
}
