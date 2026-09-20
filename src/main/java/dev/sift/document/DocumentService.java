package dev.sift.document;

import dev.sift.common.PageResponse;
import dev.sift.document.dto.CreateDocumentRequest;
import dev.sift.document.dto.DocumentResponse;
import dev.sift.document.dto.DocumentSummaryResponse;
import dev.sift.document.dto.DocumentVersionResponse;
import dev.sift.document.dto.DocumentVersionSummaryResponse;
import dev.sift.document.dto.UpdateDocumentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Knowledge base operations. Every edit also writes a version snapshot.
 */
@Service
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int MAX_VERSIONS_PER_DOCUMENT = 20;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    @Transactional
    public DocumentResponse create(Long userId, CreateDocumentRequest request) {
        Document document = new Document(userId, request.title(), request.content());

        Document saved = documentRepository.save(document);

        documentVersionRepository.save(DocumentVersion.snapshotOf(saved, 1));

        log.info("文件建立成功 documentId={} userId={}", saved.getId(), userId);

        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> findAll(Long userId, String keyword, Pageable pageable) {
        Page<DocumentSummary> page = hasKeyword(keyword)
                ? documentRepository.findSummariesByUserIdAndDeletedAtIsNullAndTitleContainingIgnoreCase(
                        userId, keyword.trim(), pageable)
                : documentRepository.findSummariesByUserIdAndDeletedAtIsNull(userId, pageable);

        return PageResponse.from(page, DocumentSummaryResponse::from);
    }

    private boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.isBlank();
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(Long userId, Long documentId) {
        Document document = documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);

        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse update(Long userId, Long documentId, UpdateDocumentRequest request) {
        Document document = documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);

        if (!document.getVersion().equals(request.version())) {
            log.info("編輯衝突 documentId={} 資料庫版本={} 呼叫端版本={}",
                    documentId, document.getVersion(), request.version());
            throw new DocumentConflictException(document.getVersion());
        }

        document.update(request.title(), request.content());

        documentRepository.flush();

        int nextVersionNumber = nextVersionNumberFor(documentId);
        documentVersionRepository.save(DocumentVersion.snapshotOf(document, nextVersionNumber));

        pruneOldVersions(documentId, nextVersionNumber);

        log.info("文件更新成功 documentId={} userId={} version={} 版本歷史={}",
                documentId, userId, document.getVersion(), nextVersionNumber);

        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionSummaryResponse> findVersions(Long userId, Long documentId) {
        requireOwnedDocument(userId, documentId);

        return documentVersionRepository
                .findSummariesByDocumentIdOrderByVersionNumberDesc(documentId)
                .stream()
                .map(DocumentVersionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse findVersion(Long userId, Long documentId, Integer versionNumber) {
        requireOwnedDocument(userId, documentId);

        DocumentVersion version = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(DocumentNotFoundException::new);

        return DocumentVersionResponse.from(version);
    }

    private void requireOwnedDocument(Long userId, Long documentId) {
        documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private int nextVersionNumberFor(Long documentId) {
        Integer max = documentVersionRepository.findMaxVersionNumber(documentId);
        return max == null ? 1 : max + 1;
    }

    private void pruneOldVersions(Long documentId, int latestVersionNumber) {
        int oldestToKeep = latestVersionNumber - MAX_VERSIONS_PER_DOCUMENT + 1;

        if (oldestToKeep <= 1) {
            return;
        }

        int deleted = documentVersionRepository.deleteOlderThan(documentId, oldestToKeep - 1);

        if (deleted > 0) {
            log.debug("修剪舊版本 documentId={} 刪除={} 筆", documentId, deleted);
        }
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        Document document = documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);

        document.markDeleted();

        log.info("文件刪除成功 documentId={} userId={}", documentId, userId);
    }
}
