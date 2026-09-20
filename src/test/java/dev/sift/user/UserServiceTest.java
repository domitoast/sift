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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("註冊成功時，email 應被正規化為小寫並去除前後空白")
    void register_shouldNormalizeEmail() {
        RegisterRequest request = new RegisterRequest("  Kevin@Example.COM  ", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$fakeHash");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.register(request);

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

        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedValue");

        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    @DisplayName("email 已存在時應丟出例外，且不呼叫 save")
    void register_shouldThrow_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("kevin@example.com", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull("kevin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("並發情況下資料庫拋出唯一約束衝突，應轉為 EmailAlreadyUsedException")
    void register_shouldTranslateConstraintViolation() {
        RegisterRequest request = new RegisterRequest("kevin@example.com", "password123");

        when(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$fakeHash");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }
}
