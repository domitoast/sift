package dev.sift.fetch;

import java.time.Instant;

/**
 * 從 feed 解析出來的一篇文章。
 *
 * <p>這是<b>還沒進資料庫</b>的中間結果——只是「我從那個網址讀到了這些」。
 * 去重、產生摘要、決定要不要存，都是後面的事。
 *
 * @param title       標題
 * @param link        原文網址
 * @param content     內文。<b>可能是 null</b>——有些 feed 只給標題和連結。
 *                    RSS 放在 {@code <description>}，Atom 放在 {@code <content>}
 * @param publishedAt 發布時間。<b>可能是 null</b>——很多 feed 不填這個欄位
 */
public record FetchedArticle(String title, String link, String content, Instant publishedAt) {
}
