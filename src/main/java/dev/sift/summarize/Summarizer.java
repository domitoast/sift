package dev.sift.summarize;

/**
 * A summarization provider. Implementations declare which provider they are,
 * so adding one requires no changes anywhere else.
 */
public interface Summarizer {
    LlmProvider provider();

    String summarize(String title, String content, String apiKey);
}
