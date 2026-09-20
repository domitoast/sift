package dev.sift.fetch;

import dev.sift.document.Document;
import dev.sift.document.DocumentRepository;
import dev.sift.document.dto.DocumentResponse;
import dev.sift.fetch.dto.FetchedItemDetailResponse;
import dev.sift.source.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes a fetched article into the knowledge base, or discards it.
 */
@Service
public class PromoteService {
    private static final Logger log = LoggerFactory.getLogger(PromoteService.class);

    private final FetchedItemRepository fetchedItemRepository;
    private final SourceRepository sourceRepository;
    private final DocumentRepository documentRepository;

    public PromoteService(FetchedItemRepository fetchedItemRepository,
                          SourceRepository sourceRepository,
                          DocumentRepository documentRepository) {
        this.fetchedItemRepository = fetchedItemRepository;
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentResponse promote(Long userId, Long itemId) {
        FetchedItem item = loadOwned(userId, itemId);

        item.promote();

        Document document = Document.fromFetchedItem(
                userId, item.getId(), item.getTitle(), buildContent(item));

        Document saved = documentRepository.saveAndFlush(document);

        log.debug("收進知識庫 itemId={} documentId={}", itemId, saved.getId());

        return DocumentResponse.from(saved);
    }

    @Transactional
    public void resummarize(Long userId, Long itemId) {
        FetchedItem item = loadOwned(userId, itemId);
        item.resummarize();

        log.debug("排入重新摘要 itemId={}", itemId);
    }

    @Transactional(readOnly = true)
    public FetchedItemDetailResponse findDetail(Long userId, Long itemId) {
        return FetchedItemDetailResponse.from(loadOwned(userId, itemId));
    }

    @Transactional
    public void discard(Long userId, Long itemId) {
        loadOwned(userId, itemId).discard();
    }

    private FetchedItem loadOwned(Long userId, Long itemId) {
        FetchedItem item = fetchedItemRepository.findById(itemId)
                .orElseThrow(FetchedItemNotFoundException::new);

        sourceRepository.findByIdAndUserIdAndDeletedAtIsNull(item.getSourceId(), userId)
                .orElseThrow(FetchedItemNotFoundException::new);

        return item;
    }

    private String buildContent(FetchedItem item) {
        return item.getSummary()
                + "\n\n---\n原文：" + item.getExternalUrl();
    }
}
