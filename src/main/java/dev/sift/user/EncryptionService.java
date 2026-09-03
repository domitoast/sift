package dev.sift.user;

import dev.sift.config.EncryptionProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 對稱加密／解密，用於保管使用者的 LLM API key（ADR-003 BYOK）。
 *
 * <h2>為什麼是加密而不是雜湊</h2>
 *
 * 密碼與 refresh token 都用雜湊，因為系統只需要「比對是不是同一個」，
 * 永遠不需要拿回原始值。
 *
 * <p>API key 不一樣——要把原始的那串字放進 HTTP 標頭送給 LLM 供應商。
 * <b>雜湊之後就永遠拿不回來了。</b>
 *
 * <p>⚠️ 反過來說：<b>能用雜湊就不要用加密。</b>
 * 加密一定會多出一把要保管的金鑰，也就多出「金鑰外洩」這一整類風險。
 * 雜湊沒有金鑰，就沒有那個風險。
 *
 * <h2>格式</h2>
 *
 * <pre>
 * Base64( [ IV 12 bytes ][ 密文 ][ GCM 認證標記 16 bytes ] )
 * </pre>
 *
 * IV 不需要保密，但解密時必須用同一個，所以跟密文存在一起。
 *
 * <p>這個類別不碰資料庫、不碰網路——字串進、字串出。
 */
@Service
public class EncryptionService {

    /** GCM 建議的 IV 長度就是 12 bytes，不要改成別的值。 */
    private static final int IV_LENGTH = 12;

    /** 認證標記的長度，單位是 bit。128 是標準值。 */
    private static final int TAG_LENGTH_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * SecureRandom 而不是 Random。
     *
     * <p>Random 是可預測的——給定同一個種子會產生同一串數字。
     * 用它產生 IV，攻擊者就能預測下一個 IV。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    private final SecretKeySpec key;

    public EncryptionService(EncryptionProperties properties) {

        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());

        /*
         * 在啟動時就檢查長度，而不是等第一次加密才爆。
         *
         * 這是 fail fast：設定錯誤應該在啟動時被發現，
         * 而不是在某個使用者設定 API key 的時候才出現 500。
         */
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "sift.encryption.secret 必須是 256 bits（Base64 解碼後 32 bytes），"
                    + "目前是 " + keyBytes.length + " bytes");
        }

        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * @param plainText 原始文字
     * @return Base64 字串，內含 IV、密文與認證標記
     */
    public String encrypt(String plainText) {

        /*
         * 每次加密都產生一個新的隨機 IV。
         *
         * 少了它會怎樣：同樣的明文永遠產生同樣的密文。
         * 兩個使用者剛好用同一把 API key，資料庫裡的兩筆密文就會一模一樣——
         * 不用解密也能看出「這兩個人用同一把 key」。
         *
         * 與 BCrypt 的 salt 是同一個道理。
         */
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV 接在密文前面，一起存。IV 不是機密，但解密時需要它
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            /*
             * ⚠️ 例外訊息裡絕對不能出現 plainText。
             *
             * 例外訊息很可能被寫進日誌，而這裡的明文就是 API key 本身。
             * 這是 ADR-003 的第三條規定：key 不得出現在任何日誌中。
             */
            throw new IllegalStateException("加密失敗", e);
        }
    }

    /**
     * @param encrypted {@link #encrypt} 產生的字串
     * @return 原始文字
     * @throws IllegalStateException 密文被竄改過、或金鑰不對
     */
    public String decrypt(String encrypted) {

        byte[] combined = Base64.getDecoder().decode(encrypted);

        // 前 12 bytes 是 IV，剩下的是密文 + 認證標記
        byte[] iv = new byte[IV_LENGTH];
        byte[] cipherText = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

        } catch (Exception e) {
            /*
             * GCM 的認證標記對不上時會走到這裡——代表密文被改過，
             * 或是換了一把金鑰。
             *
             * 這是 GCM 的價值：它會直接拒絕，而不是回傳一堆垃圾讓你以為解開了。
             */
            throw new IllegalStateException("解密失敗：密文可能被竄改，或金鑰不正確", e);
        }
    }
}
