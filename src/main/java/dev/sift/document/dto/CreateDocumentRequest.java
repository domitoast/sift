package dev.sift.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 建立文件的請求。
 *
 * <p>注意這裡「沒有」userId——擁有者從 token 取得，不能由呼叫端指定。
 * 若加上這個欄位，任何人都能以別人的名義建立文件。
 *
 * @param title   標題，上限對應資料庫的 VARCHAR(500)
 * @param content 內文
 */
public record CreateDocumentRequest(

        @NotBlank
        @Size(max = 500)
        String title,

        /*
         * 資料庫是 TEXT，沒有長度上限。但「沒有上限」不等於「不用檢查」——
         * 不設限的話，任何人可以送 100 MB 的字串進來，
         * 一次請求就吃掉伺服器的記憶體。
         *
         * 100 萬字元約 1 MB，對一篇筆記來說綽綽有餘。
         * 這個數字是估的，日後若有人真的碰到再放寬。
         */
        @NotBlank
        @Size(max = 1_000_000)
        String content
) {
}
