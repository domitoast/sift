package dev.sift.auth;

/**
 * Wrong email or password. Both cases share one message so the response
 * cannot be used to enumerate registered accounts.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("帳號或密碼錯誤");
    }
}
