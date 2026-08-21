package dev.sift.auth;

import dev.sift.auth.dto.LoginRequest;
import dev.sift.auth.dto.TokenResponse;
import dev.sift.config.JwtProperties;
import dev.sift.user.User;
import dev.sift.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登入相關的業務邏輯。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenTtlSeconds;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenTtlSeconds = jwtProperties.accessTokenTtlMinutes() * 60L;
    }

    /**
     * 驗證帳號密碼，成功則簽發 access token。
     *
     * @throws InvalidCredentialsException 帳號不存在或密碼錯誤
     */
    @Transactional(readOnly = true)
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

        log.info("登入成功 userId={}", user.getId());

        return TokenResponse.bearer(accessToken, accessTokenTtlSeconds);
    }
}
