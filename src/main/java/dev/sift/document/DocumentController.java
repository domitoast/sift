package dev.sift.document;

import dev.sift.common.PageResponse;
import dev.sift.document.dto.CreateDocumentRequest;
import dev.sift.document.dto.DocumentResponse;
import dev.sift.document.dto.DocumentSummaryResponse;
import dev.sift.document.dto.UpdateDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 文件的 HTTP 入口。
 *
 * <p>這個 class 底下的所有路徑都需要登入——{@code SecurityConfig} 的
 * {@code anyRequest().authenticated()} 已經涵蓋，這裡不必再寫任何設定。
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 建立文件。
     *
     * <p>兩個參數來自兩個不同的地方：
     * <ul>
     *   <li>{@code userId} — SecurityContext（我們驗證過的，改不了）</li>
     *   <li>{@code request} — HTTP body（呼叫端自己填的，只能拿來當內容，不能拿來當身分）</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateDocumentRequest request) {

        DocumentResponse response = documentService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/documents/" + response.id()))
                .body(response);
    }

    /**
     * 分頁列出自己的文件，最新的在前。
     *
     * <p><b>{@code @PageableDefault}</b> 設定「呼叫端沒指定時」的預設值。
     * 沒有它的話 Spring 的預設是每頁 20 筆、<b>不排序</b>——不排序的分頁是壞的。
     *
     * <p>呼叫端可以覆寫：{@code ?page=1&size=50&sort=title,asc}
     *
     * <p>每頁筆數上限由 {@code application.yml} 的
     * {@code spring.data.web.pageable.max-page-size} 控制。
     * <b>那個上限是必要的</b>：否則有人送 {@code ?size=999999999}，
     * 分頁就等於不存在。
     */
    @GetMapping
    public PageResponse<DocumentSummaryResponse> list(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return documentService.findAll(userId, pageable);
    }

    /**
     * 讀取單篇文件。
     *
     * <p>{@code @PathVariable} 取出網址中 {@code {id}} 那一段。
     * 例如 {@code GET /api/v1/documents/3} 會讓 {@code id = 3}。
     *
     * <p>⚠️ 那個 3 是<b>呼叫端自己打的</b>，任何人都能改成別的數字。
     * 因此它只能當作「要找哪一篇」，不能當作「有沒有權限」的依據——
     * 權限由 {@code userId} 決定，而 {@code userId} 來自簽章驗證過的 token。
     */
    @GetMapping("/{id}")
    public DocumentResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        return documentService.findById(userId, id);
    }

    /**
     * 編輯文件。
     *
     * <p>{@code PUT} 的語意是「用我給的內容整個取代」，
     * 因此標題與內文都必須提供。
     * （只改其中一個欄位的話會用 {@code PATCH}，本專案不做。）
     */
    @PutMapping("/{id}")
    public DocumentResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request) {

        return documentService.update(userId, id, request);
    }

    /**
     * 刪除文件（soft delete）。
     *
     * <p>回 <b>204 No Content</b>：刪除成功之後沒有任何內容要回傳。
     *
     * <p>刪別人的、刪不存在的、刪已經刪過的——一律 404，三種情況不區分。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {

        documentService.delete(userId, id);

        return ResponseEntity.noContent().build();
    }
}
