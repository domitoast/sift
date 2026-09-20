package dev.sift.user;

import dev.sift.summarize.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * An account, holding the password hash and the encrypted LLM API key.
 */
@Entity

@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "llm_api_key_encrypted")
    private String llmApiKeyEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", length = 20)
    private LlmProvider llmProvider;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public void updateLlmApiKey(LlmProvider provider, String encrypted) {
        this.llmProvider = provider;
        this.llmApiKeyEncrypted = encrypted;
    }

    public void clearLlmApiKey() {
        this.llmProvider = null;
        this.llmApiKeyEncrypted = null;
    }

    public boolean hasLlmApiKey() {
        return llmApiKeyEncrypted != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getLlmApiKeyEncrypted() {
        return llmApiKeyEncrypted;
    }

    public LlmProvider getLlmProvider() {
        return llmProvider;
    }

    public void setLlmApiKeyEncrypted(String llmApiKeyEncrypted) {
        this.llmApiKeyEncrypted = llmApiKeyEncrypted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }
}
