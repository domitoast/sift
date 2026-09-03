package dev.sift.user;

import dev.sift.config.EncryptionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 加解密的 unit test。不碰資料庫、不碰網路。
 */
class EncryptionServiceTest {

    /** 解碼後正好 32 bytes。 */
    private static final String KEY = "c2lmdC10ZXN0LWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=";

    private final EncryptionService service =
            new EncryptionService(new EncryptionProperties(KEY));

    private static final String API_KEY = "sk-ant-api03-abcdefghijklmnop";

    // ---------- 基本行為 ----------

    @Test
    @DisplayName("加密之後能解回原來的值")
    void encryptThenDecrypt_shouldRoundTrip() {

        String encrypted = service.encrypt(API_KEY);

        assertThat(service.decrypt(encrypted)).isEqualTo(API_KEY);
    }

    @Test
    @DisplayName("★ 密文裡看不到原始的值")
    void encrypt_shouldNotContainPlainText() {

        /*
         * 最基本的一題：如果密文裡還看得到原文，那整件事白做了。
         */
        assertThat(service.encrypt(API_KEY)).doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("中文也要能正確還原")
    void encrypt_chinese_shouldRoundTrip() {

        String text = "這是一段中文的機密資料";

        assertThat(service.decrypt(service.encrypt(text))).isEqualTo(text);
    }

    // ---------- IV 的作用 ----------

    @Test
    @DisplayName("★★ 同樣的明文，每次加密要產生不同的密文")
    void encrypt_sameInput_shouldProduceDifferentCipherText() {

        /*
         * 這一題在保護「每次加密都用新的隨機 IV」。
         *
         * 少了 IV 會怎樣：同樣的明文永遠產生同樣的密文。
         * 兩個使用者剛好用同一把 API key，資料庫裡兩筆密文就會一模一樣——
         * 不用解密也能看出「這兩個人用同一把 key」。
         *
         * 與 BCrypt 的 salt 是同一個道理。
         */
        String first = service.encrypt(API_KEY);
        String second = service.encrypt(API_KEY);

        assertThat(first).isNotEqualTo(second);

        // 但兩個都要解得回同一個值
        assertThat(service.decrypt(first)).isEqualTo(API_KEY);
        assertThat(service.decrypt(second)).isEqualTo(API_KEY);
    }

    // ---------- GCM 的作用 ----------

    @Test
    @DisplayName("★★ 密文被改過 → 直接拒絕，而不是回傳垃圾")
    void decrypt_tamperedCipherText_shouldThrow() {

        /*
         * 這一題在保護 GCM 這個選擇。
         *
         * 換成沒有認證標記的模式（例如 AES/CBC），改動過的密文
         * 會「成功」解密成一堆亂碼，而程式完全不知道它被改過。
         *
         * GCM 會直接拒絕。
         */
        String encrypted = service.encrypt(API_KEY);

        // 動一個字元
        char[] chars = encrypted.toCharArray();
        chars[chars.length - 3] = chars[chars.length - 3] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失敗");
    }

    @Test
    @DisplayName("★ 用另一把金鑰解不開")
    void decrypt_withDifferentKey_shouldThrow() {

        String encrypted = service.encrypt(API_KEY);

        EncryptionService other = new EncryptionService(
                new EncryptionProperties("YW5vdGhlci10ZXN0LWtleS13aXRoLTMyLWJ5dGVzISE="));

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- 設定錯誤要在啟動時就爆 ----------

    @Test
    @DisplayName("★ 金鑰長度不對 → 建立時就失敗，不要等到第一次加密")
    void constructor_wrongKeyLength_shouldFailFast() {

        /*
         * fail fast：設定錯誤應該在啟動時被發現。
         *
         * 若不檢查，程式會正常啟動，然後在某個使用者設定 API key 的時候
         * 突然回 500——而那時候沒有人知道原因是設定檔。
         */
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties("dG9vLXNob3J0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
