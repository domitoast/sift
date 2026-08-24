package dev.sift.auth.dto;

/**
 * 登入或換發成功後回傳的兩張票。
 *
 * <p>{@code tokenType} 固定是 "Bearer"，這是 HTTP 認證的標準寫法。
 * 前端拿到 accessToken 後，之後每個請求要這樣送：
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
 * </pre>
 * "Bearer" 的字面意思是「持有者」——「誰持有這張票，誰就是本人」。
 *
 * <p>{@code expiresInSeconds} 指的是 <b>accessToken</b> 的壽命，
 * 讓前端知道什麼時候該去換新的，不必自己解析 token 內容。
 *
 * <p><b>refreshToken 的壽命刻意不回傳。</b>
 * 前端不需要知道——它的策略是「打 API 收到 401 就去換一次」，
 * 而不是「算時間主動去換」。多給一個欄位只會多一種被誤用的方式。
 *
 * @param accessToken      每個 API 請求都要帶的短期憑證
 * @param refreshToken     只用於 {@code POST /auth/refresh} 的長期憑證
 * @param tokenType        固定為 "Bearer"
 * @param expiresInSeconds accessToken 多少秒後過期
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {

    public static TokenResponse bearer(String accessToken,
                                       String refreshToken,
                                       long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
