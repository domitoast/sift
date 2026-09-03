package dev.sift.user;

import dev.sift.user.dto.RegisterRequest;
import dev.sift.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * 使用者相關的業務邏輯。
 *
 * <p>{@code @Service} 讓 Spring 在啟動時把這個類別建立成 Bean。
 * 它在功能上等同 {@code @Component}，差別只在語意——
 * 讀程式碼的人一看就知道這是業務邏輯層。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    /**
     * 建構子注入（constructor injection）。
     *
     * <p>只有一個建構子時不需要寫 {@code @Autowired}，Spring 會自動注入。
     *
     * <p>兩個欄位都是 {@code final}：物件建立後不可能被替換。
     * 而且若 Spring 找不到對應的 Bean，<b>啟動時就會失敗</b>，
     * 而不是等到執行到那一行才丟 NullPointerException。
     */
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
    }

    /**
     * 註冊新使用者。
     *
     * <p>流程：
     * <ol>
     *   <li>正規化 email（去空白、轉小寫）</li>
     *   <li>檢查是否已被註冊 → 給友善錯誤</li>
     *   <li>雜湊密碼</li>
     *   <li>寫入資料庫</li>
     *   <li>轉成對外的 DTO</li>
     * </ol>
     *
     * <p>{@code @Transactional} 讓此方法內的資料庫操作成為一個交易：
     * 正常結束則提交，丟出例外則整批回滾。
     *
     * @throws EmailAlreadyUsedException email 已被註冊
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {

        /*
         * 再次正規化 email。
         *
         * RegisterRequest 的 compact constructor 已經做過一次，
         * 這裡是第二道保險（defence in depth）。
         *
         * 保留的理由：
         *   1. Service 是 public API，可能被其他程式碼直接呼叫，
         *      而不是每次都經過 Controller 與那個 DTO
         *   2. 單元測試會直接建構 RegisterRequest 呼叫 Service，
         *      這條防線讓 Service 本身的正確性不依賴上游
         *
         * 對已正規化的字串再做一次是無害的（冪等操作）。
         */
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        /*
         * 第一道防線：先查詢，目的是給使用者清楚的錯誤訊息。
         *
         * ⚠️ 這不是唯一性的保證。查詢與寫入之間存在時間空隙，
         * 兩個並發請求可能同時通過這道檢查（race condition）。
         */
        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        // 雜湊密碼。從這行之後，明文密碼不再出現於任何地方。
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(normalizedEmail, passwordHash);

        try {
            User saved = userRepository.save(user);

            /*
             * 日誌只記 id，不記 email。
             * email 屬於個人資料，密碼雜湊更是絕對不可入日誌（NFR-3.1）。
             */
            log.info("使用者註冊成功 userId={}", saved.getId());

            return UserResponse.from(saved);

        } catch (DataIntegrityViolationException e) {
            /*
             * 第二道防線：資料庫的 partial unique index。
             *
             * 走到這裡代表上面的 existsBy 檢查通過了，但寫入時仍撞到唯一約束——
             * 也就是有另一個請求在這中間搶先寫入。
             *
             * 這正是 Day 3 教的 race condition，
             * 也是「為什麼程式檢查不能取代資料庫約束」的實例。
             *
             * 把它轉成與第一道防線相同的例外，讓呼叫端看到一致的行為。
             */
            log.warn("註冊時發生唯一約束衝突，判定為並發重複註冊");
            throw new EmailAlreadyUsedException();
        }
    }

    /**
     * 依 id 查詢使用者，供 {@code GET /api/v1/me} 使用。
     *
     * <p>{@code readOnly = true} 告訴 Hibernate 這是唯讀交易，
     * 可跳過 dirty checking（比對物件有沒有被改過），省下記憶體與 CPU。
     * <b>慣例：所有查詢方法都標 readOnly = true。</b>
     *
     * <p>使用 {@code findByIdAndDeletedAtIsNull} 而非內建的 {@code findById}，
     * 讓 soft delete 的過濾發生在資料庫端。
     *
     * @throws UserNotFoundException 查無此人，或該帳號已被刪除
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {

        Optional<User> found = userRepository.findByIdAndDeletedAtIsNull(id);

        if (found.isEmpty()) {
            /*
             * 何時會走到這裡？
             *
             * token 有效代表簽發當下這個人是存在的，
             * 但在 token 的 15 分鐘有效期內帳號可能已被刪除。
             *
             * 這是一個「罕見但必須處理」的情況——
             * 不處理的話會是 NoSuchElementException，最後變成 500。
             */
            throw new UserNotFoundException();
        }

        User user = found.get();

        return UserResponse.from(user, maskedKeyOf(user));
    }

    /**
     * 設定或更新 LLM API key（ADR-003 BYOK）。
     *
     * <p>流程：明文進來 → 立刻加密 → 存加密後的值。
     * <b>明文除了這個方法的參數之外，不出現在任何地方</b>：
     * 不進日誌、不進資料庫、不回傳。
     *
     * @throws UserNotFoundException 查無此人
     */
    @Transactional
    public UserResponse updateLlmApiKey(Long userId, String rawApiKey) {

        User user = loadActive(userId);

        user.updateLlmApiKey(encryptionService.encrypt(rawApiKey));

        /*
         * 日誌只記 userId，不記 key，也不記 key 的長度或前綴。
         *
         * 「只記前四碼應該還好吧」——不要開這個頭。
         * 日誌會被複製、會被送到第三方的收集服務、會被截圖貼進聊天室。
         */
        log.info("LLM API key 已更新 userId={}", userId);

        return UserResponse.from(user, maskedKeyOf(user));
    }

    /**
     * 移除 LLM API key。
     *
     * <p>移除之後，這個使用者的文章會停在 {@code NEW} 狀態，
     * <b>不視為失敗</b>——ADR-003 明訂「若 User 未設定 key，
     * FetchedItem 停留在 NEW 狀態，不視為失敗」。
     */
    @Transactional
    public void clearLlmApiKey(Long userId) {

        loadActive(userId).clearLlmApiKey();

        log.info("LLM API key 已移除 userId={}", userId);
    }

    /**
     * 取出解密後的 API key，供摘要工作使用。
     *
     * <p><b>這是整個系統唯一一個會產生明文 key 的地方。</b>
     * 呼叫端拿到之後應該立刻用掉，不要存起來、不要放進任何物件的欄位。
     *
     * @return 明文的 key；未設定時回傳 {@code null}
     */
    @Transactional(readOnly = true)
    public String findDecryptedApiKey(Long userId) {

        User user = loadActive(userId);

        if (!user.hasLlmApiKey()) {
            return null;
        }

        return encryptionService.decrypt(user.getLlmApiKeyEncrypted());
    }

    private User loadActive(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * 產生遮罩形式，例如 {@code sk-a...mnop}。
     *
     * <p>為什麼要露出頭尾各四碼而不是全部遮掉：
     * 使用者需要能確認「我貼上去的是不是正確的那一把」。
     * 這是 Stripe、AWS 等服務的通用做法。
     *
     * <p>太短的字串一律全遮——露出頭尾對一個 8 字元的字串來說等於沒遮。
     */
    private String maskedKeyOf(User user) {

        if (!user.hasLlmApiKey()) {
            return null;
        }

        String plain = encryptionService.decrypt(user.getLlmApiKeyEncrypted());

        if (plain.length() < 12) {
            return "****";
        }

        return plain.substring(0, 4) + "..." + plain.substring(plain.length() - 4);
    }
}
