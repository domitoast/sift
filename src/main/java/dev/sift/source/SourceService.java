package dev.sift.source;

import dev.sift.fetch.FeedResolver;
import dev.sift.fetch.FetchJobRepository;
import dev.sift.fetch.dto.FetchJobResponse;
import dev.sift.source.dto.CreateSourceRequest;
import org.springframework.data.domain.Limit;
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
    private final FetchJobRepository fetchJobRepository;
    private final FeedResolver feedResolver;

    public SourceService(SourceRepository sourceRepository,
                         FetchJobRepository fetchJobRepository,
                         FeedResolver feedResolver) {
        this.sourceRepository = sourceRepository;
        this.fetchJobRepository = fetchJobRepository;
        this.feedResolver = feedResolver;
    }

    /**
     * 新增訂閱來源。
     *
     * <p><b>建立當下就會去抓一次</b>（ADR-017）：確認網址真的能用，
     * 而且如果使用者填的是網站首頁，會自動找出真正的 feed 網址。
     *
     * <p><b>這個方法刻意沒有 {@code @Transactional}</b>：
     * 中間夾著一個最多 10 秒的 HTTP 請求。包在交易裡會佔住資料庫連線 10 秒，
     * 理由與 {@code FetchService} 相同。
     *
     * <p>不需要交易也沒關係——這裡只有一次寫入，
     * 而 Spring Data 的 {@code saveAndFlush} 本身就在自己的交易裡執行。
     *
     * @throws dev.sift.fetch.FeedNotFoundException  網址不是 feed，也找不到 feed
     * @throws dev.sift.fetch.FeedFetchException     連不上、404、超時
     * @throws SourceAlreadySubscribedException      這個使用者已訂閱過同一個網址
     */
    public SourceResponse create(Long userId, CreateSourceRequest request) {

        /*
         * 先驗證再檢查重複，順序是刻意的。
         *
         * 因為「重複」要看的是「解析後的網址」：
         *   使用者先訂閱 https://blog.com        → 存成 https://blog.com/feed.xml
         *   使用者又訂閱 https://blog.com/feed.xml
         *
         * 兩次填的字串不一樣，但指的是同一個 feed。
         * 拿使用者填的原始字串去比對，這種情況會漏掉。
         *
         * 代價：重複送出時會白白抓一次網路。
         * 新增來源是低頻操作，這個代價可以接受。
         */
        String feedUrl = feedResolver.resolve(request.url());

        /*
         * 第一道防線：先查詢，目的是給使用者清楚的錯誤訊息。
         *
         * 帶 DeletedAtIsNull 是刻意的——來源被 soft delete 之後應該可以重新訂閱。
         * 少了這個條件，使用者刪掉一個來源就永遠加不回來。
         *
         * ⚠️ 這不是唯一性的保證。查詢與寫入之間有空隙，
         * 使用者連點兩下「新增」就可能讓兩個請求同時通過這道檢查。
         */
        if (sourceRepository.existsByUrlAndUserIdAndDeletedAtIsNull(feedUrl, userId)) {
            throw new SourceAlreadySubscribedException();
        }

        Source source = new Source(userId, request.name(), feedUrl, request.type());

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
     * 某來源最近幾次的抓取結果（FR-2.4）。
     *
     * <p><b>為什麼要分兩步查</b>：
     * 其他地方的權限檢查都是寫進查詢條件的（ADR-013），
     * 但 {@code fetch_job} 依 ADR-012 只存 {@code source_id}，沒有 {@code user_id}，
     * 所以沒辦法一次查完。
     *
     * <p>因此先確認這個 source 屬於這個使用者（查不到就 404），
     * 通過之後才去撈它的抓取紀錄。
     *
     * <p><b>第一步不可以省略。</b> 少了它，任何人只要猜 sourceId
     * 就能看到別人訂閱了什麼——那是 IDOR。
     *
     * @throws SourceNotFoundException 來源不存在、已刪除，或不屬於這個使用者
     */
    @Transactional(readOnly = true)
    public List<FetchJobResponse> findFetchJobs(Long userId, Long sourceId, int limit) {

        sourceRepository.findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        return fetchJobRepository
                .findBySourceIdOrderByCreatedAtDesc(sourceId, Limit.of(limit))
                .stream()
                .map(FetchJobResponse::from)
                .toList();
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
