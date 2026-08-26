package dev.sift.source;

/**
 * 找不到訂閱來源——「不存在」與「不是你的」共用這一個例外（ADR-006）。
 */
public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException() {
        super("找不到指定的訂閱來源");
    }
}
