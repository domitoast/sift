package dev.sift.auth.dto;

/**
 * 登入成功後回傳的 token。
 *
 * <p>{@code tokenType} 固定是 "Bearer"，這是 HTTP 認證的標準寫法。
 * 前端拿到後，之後每個請求要這樣送：
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
 * </pre>
 * "Bearer" 的字面意思是「持有者」——意思是「誰持有這張票，誰就是本人」。
 *
 * <p>{@code expiresInSeconds} 讓前端知道什麼時候該換新的，
 * 不需要自己去解析 token 內容。
 *
 * <p>⚠️ 今天只有 access token。refresh token 明天加。
 *
 * @param accessToken      存取用的 token
 * @param tokenType        固定為 "Bearer"
 * @param expiresInSeconds 多少秒後過期
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
