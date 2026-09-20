package dev.sift.user;

import dev.sift.user.dto.RegisterRequest;
import dev.sift.summarize.LlmProvider;
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
 * Registration and LLM key storage. Keys are encrypted before they are persisted
 * and never returned.
 */
@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(normalizedEmail, passwordHash);

        try {
            User saved = userRepository.save(user);

            log.info("使用者註冊成功 userId={}", saved.getId());

            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("註冊時發生唯一約束衝突，判定為並發重複註冊");
            throw new EmailAlreadyUsedException();
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        Optional<User> found = userRepository.findByIdAndDeletedAtIsNull(id);

        if (found.isEmpty()) {
            throw new UserNotFoundException();
        }

        User user = found.get();

        return UserResponse.from(user, maskedKeyOf(user));
    }

    @Transactional
    public UserResponse updateLlmApiKey(Long userId, LlmProvider provider, String rawApiKey) {
        User user = loadActive(userId);

        user.updateLlmApiKey(provider, encryptionService.encrypt(rawApiKey));

        log.info("LLM API key 已更新 userId={} provider={}", userId, provider);

        return UserResponse.from(user, maskedKeyOf(user));
    }

    @Transactional
    public void clearLlmApiKey(Long userId) {
        loadActive(userId).clearLlmApiKey();

        log.info("LLM API key 已移除 userId={}", userId);
    }

    @Transactional(readOnly = true)
    public LlmCredentials findLlmCredentials(Long userId) {
        User user = loadActive(userId);

        if (!user.hasLlmApiKey()) {
            return null;
        }

        return new LlmCredentials(
                user.getLlmProvider(),
                encryptionService.decrypt(user.getLlmApiKeyEncrypted()));
    }

    public record LlmCredentials(LlmProvider provider, String apiKey) {
        @Override
        public String toString() {
            return "LlmCredentials[provider=%s, apiKey=***]".formatted(provider);
        }
    }

    private User loadActive(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(UserNotFoundException::new);
    }

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
