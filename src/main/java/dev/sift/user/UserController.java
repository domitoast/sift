package dev.sift.user;

import dev.sift.summarize.LlmProvider;
import dev.sift.summarize.SummarizerRegistry;
import dev.sift.user.dto.RegisterRequest;
import dev.sift.user.dto.SetLlmApiKeyRequest;
import dev.sift.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Registration, profile and LLM key management.
 */
@RestController
public class UserController {
    private final UserService userService;
    private final SummarizerRegistry summarizerRegistry;

    public UserController(UserService userService, SummarizerRegistry summarizerRegistry) {
        this.summarizerRegistry = summarizerRegistry;
        this.userService = userService;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);

        return ResponseEntity
                .created(URI.create("/api/v1/users/" + response.id()))
                .body(response);
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Long userId) {
        UserResponse response = userService.findById(userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/llm-providers")
    public List<LlmProvider> llmProviders() {
        return summarizerRegistry.available();
    }

    @PutMapping("/api/v1/me/llm-key")
    public ResponseEntity<UserResponse> updateLlmApiKey(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SetLlmApiKeyRequest request) {
        return ResponseEntity.ok(
                userService.updateLlmApiKey(userId, request.provider(), request.apiKey()));
    }

    @DeleteMapping("/api/v1/me/llm-key")
    public ResponseEntity<Void> clearLlmApiKey(@AuthenticationPrincipal Long userId) {
        userService.clearLlmApiKey(userId);

        return ResponseEntity.noContent().build();
    }
}
