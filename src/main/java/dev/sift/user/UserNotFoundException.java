package dev.sift.user;

/**
 * Account is missing or deleted.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("此帳號已失效，請重新登入");
    }
}
