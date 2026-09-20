package dev.sift.document;

import dev.sift.common.PageResponse;
import dev.sift.document.dto.CreateDocumentRequest;
import dev.sift.document.dto.DocumentResponse;
import dev.sift.document.dto.DocumentSummaryResponse;
import dev.sift.document.dto.DocumentVersionResponse;
import dev.sift.document.dto.DocumentVersionSummaryResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Knowledge base CRUD and version history.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/documents/" + response.id()))
                .body(response);
    }

    @GetMapping
    public PageResponse<DocumentSummaryResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return documentService.findAll(userId, q, pageable);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return documentService.findById(userId, id);
    }

    @PutMapping("/{id}")
    public DocumentResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return documentService.update(userId, id, request);
    }

    @GetMapping("/{id}/versions")
    public List<DocumentVersionSummaryResponse> listVersions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return documentService.findVersions(userId, id);
    }

    @GetMapping("/{id}/versions/{versionNumber}")
    public DocumentVersionResponse getVersion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Integer versionNumber) {
        return documentService.findVersion(userId, id, versionNumber);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        documentService.delete(userId, id);

        return ResponseEntity.noContent().build();
    }
}
