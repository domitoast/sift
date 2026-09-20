package dev.sift.user;

/**
 * That email is already registered.
 */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException() {
        super("此 email 已被註冊");
    }
}
