package dev.sift.source.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改訂閱來源。
 *
 * <p><b>兩個欄位都可以省略</b>——這是 {@code PATCH} 的語意：
 * 「只改我有給的部分」。
 *
 * <p>對照 {@code PUT}（文件編輯用的）：那是「用我給的內容整個取代」，
 * 所有欄位都必填。
 *
 * <p>沒有 {@code url} 與 {@code type}：改網址等於換成另一個來源，
 * 應該刪掉重建，否則已抓取的文章會對應到錯誤的來源。
 *
 * @param name    新名稱；null 代表不改
 * @param enabled 啟用或停用；null 代表不改
 */
public record UpdateSourceRequest(

        @Size(max = 200)
        String name,

        Boolean enabled
) {
}
