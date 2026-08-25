package dev.sift.document;

import java.time.Instant;

/**
 * 版本列表用的 projection——不含 {@code content}。
 *
 * <p>畫面上的版本列表只需要「第幾版、標題、什麼時候改的」。
 * 要看內容再點進單一版本的 endpoint。
 *
 * <p>與 {@link DocumentSummary} 同樣的理由：回傳型別決定 SQL 撈哪些欄位。
 * 20 個版本每個都帶內文的話，一次列表就是好幾百 KB 的回應。
 */
public interface DocumentVersionSummary {

    Integer getVersionNumber();

    String getTitle();

    Instant getCreatedAt();
}
