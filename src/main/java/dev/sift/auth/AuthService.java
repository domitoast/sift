package dev.sift.auth;

import dev.sift.auth.dto.LoginRequest;
import dev.sift.auth.dto.RefreshRequest;
import dev.sift.auth.dto.TokenResponse;
import dev.sift.config.JwtProperties;
import dev.sift.user.User;
import dev.sift.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 登入相關的業務邏輯。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenTtlSeconds;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenTtlSeconds = jwtProperties.accessTokenTtlMinutes() * 60L;
    }

    /**
     * 驗證帳號密碼，成功則簽發 access token 與 refresh token。
     *
     * <p>⚠️ <b>這個方法從 {@code readOnly = true} 改成一般的 {@code @Transactional}。</b>
     *
     * <p>原因：現在它會寫入 {@code refresh_token} 表。
     * 在唯讀交易裡執行寫入，Hibernate 會直接拋例外。
     *
     * <p><b>每次登入都新增一列，不會更新或刪除舊的。</b>
     * 這是刻意的——一列代表一個裝置的 session。
     * 手機登入不該把筆電踢出去。
     *
     * @throws InvalidCredentialsException 帳號不存在或密碼錯誤
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {

        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        /*
         * passwordEncoder.matches() 做的事：
         *   1. 從資料庫的雜湊值裡取出當初用的 salt
         *   2. 用同一個 salt 把使用者輸入的明文密碼再雜湊一次
         *   3. 比對兩個雜湊值
         *
         * 注意「解密後比對」是不可能的——BCrypt 不可逆。
         * 唯一的驗證方式就是「用同樣的方法再算一次，看結果一不一樣」。
         */
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登入失敗：密碼錯誤 userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = issueRefreshToken(user.getId());

        log.info("登入成功 userId={}", user.getId());

        return TokenResponse.bearer(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    /**
     * 用 refresh token 換一張新的 access token，並同時輪替 refresh token。
     *
     * <p><b>判斷順序（順序本身就是設計）</b>：
     * <pre>
     * ① 雜湊收到的值
     * ② 用 token_hash 查 → 找到且可用 → 正常換發（rotation）
     * ③ 沒找到 → 改用 previous_token_hash 查
     *      ├─ 找到 → ⚠️ 一張已被換掉的票又出現了 → 判定盜用
     *      └─ 沒找到 → 單純的無效 token
     * </pre>
     *
     * <p><b>為什麼「找到但不可用」也直接拒絕</b>：
     * 已過期或已撤銷（登出過）的票沒有任何合法用途。
     *
     * <p>⚠️ <b>{@code noRollbackFor} 是這個方法最關鍵的一個字。</b>
     *
     * <p>偵測到盜用時，我們會先「作廢該使用者所有的 token」，再拋出例外。
     * 但 {@code @Transactional} 預設遇到 RuntimeException 就整批回滾——
     * <b>那會把剛剛的作廢動作一起撤銷，攻擊者的 token 原封不動地留著。</b>
     *
     * <p>偵測到了卻沒有實際作廢，是比沒偵測更糟的結果：你以為自己防住了。
     *
     * <p>{@code noRollbackFor} 告訴 Spring：「遇到這個例外時照常提交。」
     * 於是作廢生效，例外也照樣傳到 GlobalExceptionHandler 變成 401。
     *
     * @throws RefreshTokenReuseException   偵測到重複使用
     * @throws InvalidRefreshTokenException 其他所有失敗情況
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(RefreshRequest request) {

        String presentedHash = jwtService.hashRefreshToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseGet(() -> {
                    detectReuseOrFail(presentedHash);
                    // detectReuseOrFail 一定會拋例外，這行只是讓編譯器安心
                    throw new InvalidRefreshTokenException();
                });

        if (!stored.isUsable()) {
            log.debug("refresh 失敗：token 已過期或已撤銷 tokenId={}", stored.getId());
            throw new InvalidRefreshTokenException();
        }

        /*
         * rotation：產生新值，把舊的雜湊移到 previous_token_hash。
         *
         * 注意這裡「沒有」呼叫 save()。
         *
         * stored 是在這個交易裡從資料庫載入的，屬於 Hibernate 的「管理狀態」
         * （managed）。交易提交時，Hibernate 會自動比對物件現在的欄位值
         * 與載入時的快照，發現有差異就自動送出 UPDATE。
         *
         * 這個機制叫 dirty checking（髒資料檢查）。
         * 這也正是查詢方法要標 readOnly = true 的原因——
         * 那會關掉這項比對，省下記憶體與 CPU。
         */
        String newRawToken = jwtService.generateRefreshTokenValue();
        stored.rotate(jwtService.hashRefreshToken(newRawToken));

        String accessToken = jwtService.generateAccessToken(stored.getUserId());

        log.info("token 換發成功 userId={}", stored.getUserId());

        return TokenResponse.bearer(accessToken, newRawToken, accessTokenTtlSeconds);
    }

    /**
     * 登出：作廢這一張 refresh token。
     *
     * <p><b>只作廢「這一張」，不影響同一使用者的其他裝置。</b>
     * 手機按登出，筆電應該還是登入狀態。
     * （「登出所有裝置」是另一個功能，會用 {@code revokeAllByUserId}。）
     *
     * <p><b>⚠️ 這個方法刻意「永不失敗」。</b>
     *
     * <p>token 查不到、已過期、已撤銷——一律當作成功，回 204。
     *
     * <p>兩個理由：
     * <ol>
     *   <li><b>idempotent</b>：使用者的意圖是「讓我登出」。
     *       如果那張票本來就無效，這個意圖已經達成了。
     *       重複呼叫兩次不該第二次報錯</li>
     *   <li><b>不洩漏資訊</b>：若無效的 token 回 401、有效的回 204，
     *       攻擊者就得到一支免費的「這張 token 是不是真的」查詢 API</li>
     * </ol>
     *
     * <p>這裡也<b>不做盜用偵測</b>——拿舊 token 登出是無害的行為，
     * 沒有理由因此把使用者所有裝置踢掉。
     *
     * <p>access token 不受影響，仍會在剩餘的 15 分鐘內有效。
     * 這是無狀態 JWT 的固有代價（ADR-010）：真正的登出由前端丟棄 token 完成，
     * 伺服器這一步保證的是「之後換不到新的」。
     */
    @Transactional
    public void logout(RefreshRequest request) {

        String presentedHash = jwtService.hashRefreshToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(presentedHash)
                .filter(RefreshToken::isUsable)
                .ifPresentOrElse(
                        token -> {
                            token.revoke();
                            log.info("登出成功 userId={}", token.getUserId());
                        },
                        () -> log.debug("登出時收到無效的 refresh token，視為已登出")
                );
    }

    /**
     * 查不到目前的 token 時，判斷這是「盜用」還是「單純無效」。
     *
     * <p>若這個雜湊出現在某一列的 {@code previous_token_hash}，
     * 代表它曾經有效、已經被換掉了，現在卻又被拿來使用。
     *
     * <p>正常流程下不可能發生——前端換發成功後就會丟掉舊值。
     * 因此唯一的解釋是：<b>有第二個人也持有過這張票。</b>
     *
     * <p>伺服器無法分辨兩個持有者中哪一個是本人，
     * 只能把該使用者所有 refresh token 全部作廢，讓雙方都重新登入。
     * 本人重新輸入一次密碼即可，攻擊者沒有密碼，出局。
     *
     * <p>⚠️ 已知的誤判風險：前端送出換發請求後逾時、自動重試，
     * 而第一次其實已經成功——這會被判定為盜用。
     * 正式系統會加上數秒的寬限期，本專案暫不實作。
     */
    private void detectReuseOrFail(String presentedHash) {

        refreshTokenRepository.findByPreviousTokenHash(presentedHash)
                .ifPresent(compromised -> {
                    int revokedCount = refreshTokenRepository.revokeAllByUserId(
                            compromised.getUserId(), Instant.now());

                    log.warn("偵測到 refresh token 重複使用，已作廢該使用者所有憑證 "
                             + "userId={} revokedCount={}", compromised.getUserId(), revokedCount);

                    throw new RefreshTokenReuseException();
                });

        log.debug("refresh 失敗：查無此 token");
        throw new InvalidRefreshTokenException();
    }

    /**
     * 產生一張新的 refresh token，並在資料庫留下紀錄。
     *
     * <p>四個步驟：
     * <ol>
     *   <li>用 {@code SecureRandom} 產生 32 bytes 的隨機值</li>
     *   <li>SHA-256 雜湊</li>
     *   <li>只把<b>雜湊值</b>寫進資料庫</li>
     *   <li>把<b>原始值</b>回傳給呼叫端</li>
     * </ol>
     *
     * <p><b>關鍵：原始值離開這個方法之後，伺服器就再也拿不回來了。</b>
     * 資料庫裡只有雜湊，而雜湊無法反推。
     * 這代表即使資料庫整個外洩，攻擊者也拿不到任何一張可用的 token。
     *
     * <p>與密碼完全相同的設計，只是雜湊演算法不同——
     * 密碼是人想的（要用慢的 BCrypt 拖住暴力破解），
     * 這裡是 256 bits 的密碼學隨機值（猜不到，SHA-256 就夠）。
     *
     * @return 原始的 token 值，<b>唯一一次</b>能取得它的機會
     */
    private String issueRefreshToken(Long userId) {

        String rawToken = jwtService.generateRefreshTokenValue();
        String tokenHash = jwtService.hashRefreshToken(rawToken);

        RefreshToken entity = new RefreshToken(
                userId,
                tokenHash,
                jwtService.refreshTokenExpiryFromNow()
        );

        refreshTokenRepository.save(entity);

        return rawToken;
    }
}
