package dev.sift.user.dto;

import dev.sift.user.User;

import java.time.Instant;

/**
 * 對外回傳的使用者資料。
 *
 * <p><b>這個類別存在的唯一理由，是控制「什麼可以出去」。</b>
 *
 * <p>刻意不包含的欄位：
 * <ul>
 *   <li>{@code passwordHash} — 密碼雜湊，任何情況都不對外</li>
 *   <li>{@code llmApiKeyEncrypted} — 加密的 API key（NFR-3.6 規定只能遮罩回傳）</li>
 *   <li>{@code deletedAt} — 內部狀態，對使用者無意義</li>
 *   <li>{@code updatedAt} — 目前無任何畫面需要它</li>
 * </ul>
 *
 * <p>若日後 Entity 新增了敏感欄位，這裡不會自動跟著跑出去——
 * 因為每個欄位都必須在 {@link #from(User)} 中明確寫出來。
 * 這是「預設不外洩」，而非「記得要排除」。
 *
 * @param id        使用者 id
 * @param email     登入帳號
 * @param createdAt 帳號建立時間
 */
public record UserResponse(
        Long id,
        String email,
        Instant createdAt
) {

    /**
     * 從 Entity 轉成 Response DTO。
     *
     * <p>轉換邏輯放在 DTO 這一側而非 Entity 裡，理由是
     * <b>Entity 不應該知道 API 長什麼樣子</b>。
     * 依賴方向是「DTO 認識 Entity」，不是反過來。
     *
     * <p>若讓 Entity 提供 {@code toResponse()}，domain 層就綁死了某一種
     * API 格式；日後要支援第二種格式（例如簡化版的列表回應）就會很尷尬。
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
