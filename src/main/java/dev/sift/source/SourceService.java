package dev.sift.source;

import dev.sift.source.dto.CreateSourceRequest;
import dev.sift.source.dto.SourceResponse;
import dev.sift.source.dto.UpdateSourceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 新增訂閱來源。
     *
     * <p>擋重複的方式與 {@code UserService.register()} 相同，分兩道防線。
     *
     * @throws SourceAlreadySubscribedException 這個使用者已訂閱過同一個網址
     */
    @Transactional
    public SourceResponse create(Long userId, CreateSourceRequest request) {

        /*
         * 第一道防線：先查詢，目的是給使用者清楚的錯誤訊息。
         *
         * 帶 DeletedAtIsNull 是刻意的——來源被 soft delete 之後應該可以重新訂閱。
         * 少了這個條件，使用者刪掉一個來源就永遠加不回來。
         *
         * ⚠️ 這不是唯一性的保證。查詢與寫入之間有空隙，
         * 使用者連點兩下「新增」就可能讓兩個請求同時通過這道檢查。
         */
        if (sourceRepository.existsByUrlAndUserIdAndDeletedAtIsNull(request.url(), userId)) {
            throw new SourceAlreadySubscribedException();
        }

        Source source = new Source(userId, request.name(), request.url(), request.type());

        try {
            Source saved = sourceRepository.saveAndFlush(source);

            log.info("訂閱來源建立成功 sourceId={} userId={} type={}",
                    saved.getId(), userId, saved.getType());

            return SourceResponse.from(saved);

        } catch (DataIntegrityViolationException e) {
            /*
             * 第二道防線：資料庫的 partial unique index uq_source_user_url
             * （定義在 V1__init.sql，Day 3 設計 schema 時就存在）。
             *
             * 走到這裡代表上面的 existsBy 通過了，但寫入時仍撞到唯一約束——
             * 也就是有另一個請求搶先寫入。
             *
             * 轉成與第一道防線相同的例外，呼叫端看到的行為才會一致。
             * 沒有這個 catch 的話，這個情況會變成 500。
             */
            log.warn("新增來源時發生唯一約束衝突，判定為並發重複訂閱 userId={}", userId);
            throw new SourceAlreadySubscribedException();
        }
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
