package dev.sift.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 編輯文件的請求。
 *
 * @param title   新標題
 * @param content 新內文
 * @param version <b>讀取這篇文件時拿到的 version</b>
 */
public record UpdateDocumentRequest(

        @NotBlank
        @Size(max = 500)
        String title,

        @NotBlank
        @Size(max = 1_000_000)
        String content,

        /*
         * 這個欄位是「你編輯的是哪一個版本」。
         *
         * 前端流程：GET 文件時拿到 version=3 → 使用者編輯 → PUT 時附上 version=3
         *
         * 伺服器若發現資料庫現在已經是 4，就知道中間有人改過，回 409 而不是覆蓋。
         *
         * 設為必填（@NotNull）是刻意的：允許省略的話，
         * 呼叫端只要不送這個欄位就能繞過衝突偵測——那等於沒做。
         */
        @NotNull
        @PositiveOrZero
        Long version
) {
}
