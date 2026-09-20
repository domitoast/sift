package dev.sift.user;

import dev.sift.config.EncryptionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {
    private static final String KEY = "c2lmdC10ZXN0LWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=";

    private final EncryptionService service =
            new EncryptionService(new EncryptionProperties(KEY));

    private static final String API_KEY = "sk-ant-api03-abcdefghijklmnop";

    @Test
    @DisplayName("加密之後能解回原來的值")
    void encryptThenDecrypt_shouldRoundTrip() {
        String encrypted = service.encrypt(API_KEY);

        assertThat(service.decrypt(encrypted)).isEqualTo(API_KEY);
    }

    @Test
    @DisplayName("★ 密文裡看不到原始的值")
    void encrypt_shouldNotContainPlainText() {
        assertThat(service.encrypt(API_KEY)).doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("中文也要能正確還原")
    void encrypt_chinese_shouldRoundTrip() {
        String text = "這是一段中文的機密資料";

        assertThat(service.decrypt(service.encrypt(text))).isEqualTo(text);
    }

    @Test
    @DisplayName("★★ 同樣的明文，每次加密要產生不同的密文")
    void encrypt_sameInput_shouldProduceDifferentCipherText() {
        String first = service.encrypt(API_KEY);
        String second = service.encrypt(API_KEY);

        assertThat(first).isNotEqualTo(second);

        assertThat(service.decrypt(first)).isEqualTo(API_KEY);
        assertThat(service.decrypt(second)).isEqualTo(API_KEY);
    }

    @Test
    @DisplayName("★★ 密文被改過 → 直接拒絕，而不是回傳垃圾")
    void decrypt_tamperedCipherText_shouldThrow() {
        String encrypted = service.encrypt(API_KEY);

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

    @Test
    @DisplayName("★ 金鑰長度不對 → 建立時就失敗，不要等到第一次加密")
    void constructor_wrongKeyLength_shouldFailFast() {
        assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties("dG9vLXNob3J0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
