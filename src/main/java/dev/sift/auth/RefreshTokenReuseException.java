package dev.sift.auth;

/**
 * 偵測到 refresh token 重複使用——判定為盜用。
 *
 * <p>繼承 {@link InvalidRefreshTokenException}，因此
 * {@code GlobalExceptionHandler} 上針對父類別的處理器會一併接住它，
 * <b>對外回應與一般的無效 token 完全相同</b>（皆為 401、相同訊息）。
 *
 * <p>分成兩個類別的目的只有兩個：
 * <ol>
 *   <li>伺服器端可以記不同等級的日誌（這個要 warn，一般無效只要 debug）</li>
 *   <li>測試可以精確斷言「觸發的是盜用偵測」，而不只是「回了 401」</li>
 * </ol>
 */
public class RefreshTokenReuseException extends InvalidRefreshTokenException {
}
