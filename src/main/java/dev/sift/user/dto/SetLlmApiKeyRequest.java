package dev.sift.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 設定 LLM API key 的請求。
 *
 * <p>⚠️ <b>這個 record 絕對不可以覆寫 {@code toString()} 去印出 apiKey</b>——
 * record 預設的 {@code toString()} 會印出所有欄位，而某些日誌設定
 * 會把請求物件整個印出來。
 *
 * <p>ADR-003 的第三條：API key 不得出現在任何日誌中。
 *
 * @param apiKey 原始的 API key。<b>這是唯一一個它以明文存在的地方</b>，
 *               進到 Service 之後立刻被加密
 */
public record SetLlmApiKeyRequest(

        @NotBlank(message = "API key 不可為空")
        @Size(max = 500, message = "API key 過長")
        String apiKey
) {

    /**
     * 覆寫掉 record 預設的 toString。
     *
     * <p>預設的版本長這樣：
     * <pre>SetLlmApiKeyRequest[apiKey=sk-ant-api03-真正的金鑰]</pre>
     *
     * <p>只要有任何一行 {@code log.info("request={}", request)}，
     * 那把 key 就會躺在日誌檔裡。
     *
     * <p><b>這不是「小心一點就好」，是「讓它做不到」。</b>
     */
    @Override
    public String toString() {
        return "SetLlmApiKeyRequest[apiKey=***]";
    }
}
