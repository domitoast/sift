package dev.sift.fetch;

/**
 * Whether a failure is worth retrying (TRANSIENT) or needs a human (PERMANENT).
 */
public enum FailureType {
    TRANSIENT,

    PERMANENT
}
