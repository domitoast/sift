package dev.sift.source;

import dev.sift.source.dto.CreateSourceRequest;
import dev.sift.source.dto.SourceResponse;
import dev.sift.source.dto.UpdateSourceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 訂閱來源的業務邏輯。
 *
 * <p>與 {@code DocumentService} 相同的約定：第一個參數一律是當前登入者的 id。
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public SourceResponse create(Long userId, CreateSourceRequest request) {

        Source source = new Source(userId, request.name(), request.url(), request.type());

        Source saved = sourceRepository.save(source);

        log.info("訂閱來源建立成功 sourceId={} userId={} type={}",
                saved.getId(), userId, saved.getType());

        return SourceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> findAll(Long userId) {

        return sourceRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(SourceResponse::from)
                .toList();
    }

    /**
     * 修改名稱或啟用狀態。
     *
     * <p>null 的欄位代表「不改」——這是 PATCH 的語意。
     *
     * @throws SourceNotFoundException 來源不存在、已刪除，或不屬於這個使用者
     */
    @Transactional
    public SourceResponse update(Long userId, Long sourceId, UpdateSourceRequest request) {

        Source source = sourceRepository
                .findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        if (request.name() != null && !request.name().isBlank()) {
            source.rename(request.name().trim());
        }

        if (request.enabled() != null) {
            source.setEnabled(request.enabled());
        }

        sourceRepository.flush();

        log.info("訂閱來源更新成功 sourceId={} userId={} enabled={}",
                sourceId, userId, source.isEnabled());

        return SourceResponse.from(source);
    }

    /**
     * soft delete。
     *
     * <p><b>不會刪除已經抓下來的文章</b>——{@code fetched_item} 的外鍵是
     * {@code ON DELETE RESTRICT}，而且那些文章對使用者仍有價值。
     * 刪除來源只代表「以後不要再抓了」。
     *
     * @throws SourceNotFoundException 來源不存在、已刪除，或不屬於這個使用者
     */
    @Transactional
    public void delete(Long userId, Long sourceId) {

        Source source = sourceRepository
                .findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        source.markDeleted();

        log.info("訂閱來源刪除成功 sourceId={} userId={}", sourceId, userId);
    }
}
