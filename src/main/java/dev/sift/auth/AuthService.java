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
 * Credential verification and refresh-token rotation.
 * Each refresh issues a new pair and revokes the old one; replaying a revoked
 * token is treated as theft and revokes the whole chain.
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

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("登入失敗：密碼錯誤 userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = issueRefreshToken(user.getId());

        log.info("登入成功 userId={}", user.getId());

        return TokenResponse.bearer(accessToken, refreshToken, accessTokenTtlSeconds);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(RefreshRequest request) {
        String presentedHash = jwtService.hashRefreshToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseGet(() -> {
                    detectReuseOrFail(presentedHash);

                    throw new InvalidRefreshTokenException();
                });

        if (!stored.isUsable()) {
            log.debug("refresh 失敗：token 已過期或已撤銷 tokenId={}", stored.getId());
            throw new InvalidRefreshTokenException();
        }

        String newRawToken = jwtService.generateRefreshTokenValue();
        stored.rotate(jwtService.hashRefreshToken(newRawToken));

        String accessToken = jwtService.generateAccessToken(stored.getUserId());

        log.info("token 換發成功 userId={}", stored.getUserId());

        return TokenResponse.bearer(accessToken, newRawToken, accessTokenTtlSeconds);
    }

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
