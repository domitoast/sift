package dev.sift.auth;

/**
 * refresh token 無法用於換發。
 *
 * <p>涵蓋四種情況：查無此 token、已過期、已撤銷、偵測到重複使用。
 *
 * <p><b>訊息刻意含糊，四種情況一律回相同內容。</b>
 * 若分開回應，攻擊者就能靠回應內容分辨
 * 「這張是我偽造的」與「這張曾經是真的但被撤銷了」——
 * 後者等於告訴他「你的方向是對的」。
 *
 * <p>真正的原因記在伺服器日誌，不回給呼叫端。
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("憑證無效，請重新登入");
    }
}
