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

    /**
     * 建構子注入（constructor injection）。
     *
     * <p>只有一個建構子時不需要寫 {@code @Autowired}，Spring 會自動注入。
     *
     * <p>兩個欄位都是 {@code final}：物件建立後不可能被替換。
     * 而且若 Spring 找不到對應的 Bean，<b>啟動時就會失敗</b>，
     * 而不是等到執行到那一行才丟 NullPointerException。
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return UserResponse.from(user);
    }
}
