package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    private final ContentHasher hasher = new ContentHasher();

    @Test
    @DisplayName("同樣的標題與內文 → 同樣的雜湊值")
    void hash_sameInput_shouldBeSame() {

        String a = hasher.hash("標題", "內文");
        String b = hasher.hash("標題", "內文");

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("長度固定 64 個字元，對應 CHAR(64)")
    void hash_shouldBe64HexChars() {

        assertThat(hasher.hash("標題", "內文"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("內文差一個字 → 不同的雜湊值")
    void hash_differentContent_shouldDiffer() {

        assertThat(hasher.hash("標題", "內文A"))
                .isNotEqualTo(hasher.hash("標題", "內文B"));
    }

    @Test
    @DisplayName("★ 內文是 null 也要能算，不能爆")
    void hash_nullContent_shouldWork() {

        /*
         * 很多 feed 只給標題和連結，沒有內文。
         * 如果這裡丟 NullPointerException，那些來源整批抓不進來。
         */
        assertThat(hasher.hash("只有標題", null)).hasSize(64);
    }

    @Test
    @DisplayName("★ null 內文與空字串內文視為相同")
    void hash_nullAndEmpty_shouldBeSame() {

        /*
         * 同一篇文章，某天 feed 的 <description> 是空的、某天整個欄位不見了。
         * 如果這兩種情況算出不同的雜湊值，那篇文章會被存兩次。
         */
        assertThat(hasher.hash("標題", null))
                .isEqualTo(hasher.hash("標題", ""));
    }

    @Test
    @DisplayName("★★ 標題與內文的分界不能被移動")
    void hash_shiftedBoundary_shouldDiffer() {

        /*
         * 如果實作寫成 title + content（沒有分隔字元），
         * 這兩組會算出完全一樣的雜湊值：
         *
         *   ("ab", "c")  →  "abc"
         *   ("a",  "bc") →  "abc"
         *
         * 後果是：兩篇不同的文章被當成同一篇，
         * 第二篇永遠寫不進資料庫，而且完全查不出原因。
         *
         * 加一個換行當分隔就避開了。
         */
        assertThat(hasher.hash("ab", "c"))
                .isNotEqualTo(hasher.hash("a", "bc"));
    }
}
