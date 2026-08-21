package dev.sift.user;

import dev.sift.user.dto.RegisterRequest;
import dev.sift.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 的單元測試。
 *
 * <p><b>「單元」的意思是：只測 UserService 這一個類別。</b>
 * 它依賴的 UserRepository 與 PasswordEncoder 都用假的替換，
 * 因此這些測試不啟動 Spring、不連資料庫，全部跑完約數十毫秒。
 *
 * <p><b>能這樣做的前提，是 UserService 用了建構子注入。</b>
 * 若當初在 UserService 裡直接 {@code new UserRepository()}，
 * 就沒有任何辦法把它換成假的——這就是 Day 4 教 DI 時說的
 * 「可測試性」的具體兌現。
 *
 * <p>{@code @ExtendWith(MockitoExtension.class)} 啟用 Mockito，
 * 讓下面的 {@code @Mock} 與 {@code @InjectMocks} 生效。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    /**
     * {@code @Mock} 產生一個假的 UserRepository。
     * 它的所有方法預設回傳 null / false / 空集合，
     * 直到你用 {@code when(...)} 指定行為為止。
     */
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    /**
     * {@code @InjectMocks} 建立真正的 UserService，
     * 並把上面兩個假物件透過建構子塞進去。
     */
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("註冊成功時，email 應被正規化為小寫並去除前後空白")
    void register_shouldNormalizeEmail() {

        // ---- Arrange（準備）----
        // 使用者輸入了大寫、前後有空白的 email
        RegisterRequest request = new RegisterRequest("  Kevin@Example.COM  ", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$fakeHash");
        // save 被呼叫時，原封不動回傳傳進去的那個物件
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // ---- Act（執行）----
        UserResponse response = userService.register(request);

        // ---- Assert（驗證）----
        /*
         * ArgumentCaptor 用來「攔截」傳給 save() 的那個物件，
         * 讓我們可以檢查 Service 到底組了什麼東西出來。
         *
         * 這比只檢查回傳值更有價值——回傳值是 DTO，
         * 看不到實際要寫進資料庫的內容。
         */
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("kevin@example.com");
        assertThat(response.email()).isEqualTo("kevin@example.com");
    }

    @Test
    @DisplayName("註冊時應儲存雜湊後的密碼，絕不儲存明文")
    void register_shouldStoreHashedPassword() {

        RegisterRequest request = new RegisterRequest("kevin@example.com", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedValue");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        // 存進去的是雜湊值
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedValue");
        // 而且絕不是明文
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    @DisplayName("email 已存在時應丟出例外，且不呼叫 save")
    void register_shouldThrow_whenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest("kevin@example.com", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull("kevin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class);

        /*
         * 驗證「沒有做某件事」和驗證「有做某件事」同樣重要。
         *
         * 這裡確認 save() 完全沒被呼叫——
         * 若少了這個斷言，就算 Service 先存了資料再丟例外，測試也會通過。
         */
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("並發情況下資料庫拋出唯一約束衝突，應轉為 EmailAlreadyUsedException")
    void register_shouldTranslateConstraintViolation() {

        RegisterRequest request = new RegisterRequest("kevin@example.com", "password123");

        // 模擬「檢查時還沒有，但寫入時被別人搶先」的 race condition
        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$fakeHash");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        /*
         * 呼叫端不該看到資料庫層的例外——
         * 那會洩漏實作細節，而且 Controller 無法據以回傳正確的狀態碼。
         * 應轉換成業務語意的例外。
         */
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }
}
