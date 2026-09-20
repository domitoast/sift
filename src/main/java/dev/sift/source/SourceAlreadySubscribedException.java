package dev.sift.source;

/**
 * This user already subscribes to the same resolved feed URL.
 */
public class SourceAlreadySubscribedException extends RuntimeException {
    public SourceAlreadySubscribedException() {
        super("這個來源你已經訂閱過了");
    }
}
