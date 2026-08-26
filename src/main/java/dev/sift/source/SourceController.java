package dev.sift.source;

import dev.sift.source.dto.CreateSourceRequest;
import dev.sift.source.dto.SourceResponse;
import dev.sift.source.dto.UpdateSourceRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 訂閱來源的 HTTP 入口。
 */
@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ResponseEntity<SourceResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateSourceRequest request) {

        SourceResponse response = sourceService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/sources/" + response.id()))
                .body(response);
    }

    /**
     * 列出自己的訂閱來源。
     *
     * <p>沒有分頁——來源是手動一個一個加的，幾十個就算多。
     */
    @GetMapping
    public List<SourceResponse> list(@AuthenticationPrincipal Long userId) {
        return sourceService.findAll(userId);
    }

    /**
     * 修改名稱或啟用狀態。
     *
     * <p>用 {@code PATCH} 而非 {@code PUT}：這裡是「只改我有給的欄位」，
     * 沒給的維持原樣。文件編輯用 {@code PUT}，因為那是整篇取代。
     */
    @PatchMapping("/{id}")
    public SourceResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSourceRequest request) {

        return sourceService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        sourceService.delete(userId, id);

        return ResponseEntity.noContent().build();
    }
}
