package dev.sift.source;

import dev.sift.fetch.dto.FetchJobResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 這個來源最近幾次的抓取結果（FR-2.4）。
     *
     * <p>使用者靠這支 API 知道「我的訂閱是不是壞了」。
     * 沒有它，一個 PERMANENT 失敗的來源會安靜地永遠沒有新文章，
     * 而使用者不會知道原因。
     *
     * <p>路徑是 {@code /sources/{id}/fetch-jobs} 而不是
     * {@code /fetch-jobs?sourceId=x}——抓取紀錄不會獨立存在，
     * 它一定屬於某個來源。<b>從屬關係用路徑表達，篩選條件才用查詢字串。</b>
     *
     * @param limit 最多回幾筆。預設 10，超出 1–50 的範圍會被夾回範圍內
     */
    @GetMapping("/{id}/fetch-jobs")
    public List<FetchJobResponse> fetchJobs(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {

        /*
         * 上限一定要有：沒有它，任何人送 ?limit=999999999 就等於要求全部。
         * 這與 application.yml 的 max-page-size: 100 是同一個道理。
         *
         * 選擇「夾回範圍」而不是「回 400」，是為了和分頁的行為一致——
         * Spring 的 max-page-size 也是靜靜夾住，不報錯。
         *
         * Math.clamp 是 Java 21 的新方法，等同
         * Math.min(50, Math.max(1, limit))，但讀起來清楚很多。
         */
        return sourceService.findFetchJobs(userId, id, Math.clamp(limit, 1, 50));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        sourceService.delete(userId, id);

        return ResponseEntity.noContent().build();
    }
}
