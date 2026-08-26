package dev.sift.source.dto;

import dev.sift.source.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增訂閱來源的請求。
 *
 * @param name 使用者自己取的名稱
 * @param url  RSS / Atom 的網址
 * @param type 格式。目前要使用者自己填——自動偵測需要先抓一次，而抓取尚未實作
 */
public record CreateSourceRequest(

        @NotBlank
        @Size(max = 200)
        String name,

        /*
         * 只檢查「以 http:// 或 https:// 開頭」，不做完整的網址格式驗證。
         *
         * 理由：完整驗證擋不住「格式正確但不存在」或「格式正確但不是 RSS」，
         * 那些只有真的抓一次才知道。做太嚴反而會擋掉合法但少見的網址。
         *
         * ⚠️ 限定 http/https 是安全考量：不擋的話，
         * 使用者可以填 file:///etc/passwd 之類的位址，
         * 等排程去抓時就變成讀伺服器本機檔案（SSRF 的一種）。
         */
        @NotBlank
        @Size(max = 1000)
        @Pattern(regexp = "^https?://.+", message = "網址必須以 http:// 或 https:// 開頭")
        String url,

        @NotNull
        SourceType type
) {
}
