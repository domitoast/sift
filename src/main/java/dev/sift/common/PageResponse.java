package dev.sift.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分頁回應的統一格式。
 *
 * <p><b>為什麼不直接回傳 Spring 的 {@code Page}</b>：
 *
 * <p>{@code Page} 是 Spring Data 的內部型別，它序列化出來的 JSON 結構
 * 由框架決定，而且<b>曾經隨版本變動</b>。直接回傳等於把「框架版本」
 * 變成 API 合約的一部分——升級 Spring Boot 就可能默默改變回應格式，
 * 前端在毫無預警下壞掉。
 *
 * <p>自己定義一個 record，多寫十行，換到的是「回應格式由我們決定」。
 * 這與 {@code UserResponse} 不直接回傳 Entity 是同一個原則：
 * <b>不要讓內部結構外洩成對外合約。</b>
 *
 * @param content       這一頁的內容
 * @param page          目前頁碼，從 0 開始
 * @param size          每頁筆數
 * @param totalElements 符合條件的總筆數
 * @param totalPages    總頁數
 * @param hasNext       還有沒有下一頁
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * 從 Spring 的 Page 轉換，並同時把 Entity 轉成 DTO。
     *
     * @param mapper 單筆的轉換函式，例如 {@code DocumentSummaryResponse::from}
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
