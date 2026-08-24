package dev.sift.user;

/**
 * 找不到指定的使用者（或該帳號已被刪除）。
 *
 * <p><b>繼承 RuntimeException 而非 Exception</b>，兩個理由：
 * <ol>
 *   <li>不必在每一層方法簽章寫 {@code throws}，
 *       否則從 Service 到 Controller 整條路都會被汙染</li>
 *   <li>{@code @Transactional} 預設<b>只對 RuntimeException 回滾</b>。
 *       若繼承 Exception，交易會照常提交</li>
 * </ol>
 *
 * <p><b>命名與狀態碼是兩件事</b>：這個類別的名字描述「發生了什麼」，
 * 至於要回應幾號，是 {@code GlobalExceptionHandler} 的決定。
 * 本專案選擇回 401——理由見該處說明。
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("此帳號已失效，請重新登入");
    }
}
