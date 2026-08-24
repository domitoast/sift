package dev.sift.document;

import dev.sift.common.PageResponse;
import dev.sift.document.dto.CreateDocumentRequest;
import dev.sift.document.dto.DocumentResponse;
import dev.sift.document.dto.DocumentSummaryResponse;
import dev.sift.document.dto.UpdateDocumentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件的業務邏輯。
 *
 * <p><b>約定：所有方法的第一個參數都是 {@code userId}（當前登入者）。</b>
 *
 * <p>兩個參數都是 {@code Long}，寫反了照樣編譯、照樣執行，
 * 只是永遠查不到東西。固定順序是為了讓「寫反」變得容易被看出來。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 建立一篇手動文件。
     *
     * <p>擁有者來自參數（最終來自 token），<b>不來自請求內容</b>。
     */
    @Transactional
    public DocumentResponse create(Long userId, CreateDocumentRequest request) {

        Document document = new Document(userId, request.title(), request.content());

        Document saved = documentRepository.save(document);

        // 不記標題與內文——那是使用者的私人資料
        log.info("文件建立成功 documentId={} userId={}", saved.getId(), userId);

        return DocumentResponse.from(saved);
    }

    /**
     * 分頁列出某使用者的文件。
     *
     * <p>回傳精簡版 DTO，不含內文——理由見 {@link DocumentSummaryResponse}。
     *
     * <p>排序由 Controller 決定並傳進來的 {@code Pageable} 攜帶。
     * <b>分頁一定要配排序</b>：沒有 ORDER BY 的話資料庫不保證順序，
     * 同一篇文件可能在兩頁都出現，也可能永遠不出現。
     */
    @Transactional(readOnly = true)
    public PageResponse<DocumentSummaryResponse> findAll(Long userId, Pageable pageable) {

        Page<DocumentSummary> page = documentRepository
                .findSummariesByUserIdAndDeletedAtIsNull(userId, pageable);

        return PageResponse.from(page, DocumentSummaryResponse::from);
    }

    /**
     * 讀取單篇文件。
     *
     * @throws DocumentNotFoundException 文件不存在、已刪除，或不屬於這個使用者
     */
    @Transactional(readOnly = true)
    public DocumentResponse findById(Long userId, Long documentId) {

        // 查詢條件已含 userId：查不到 = 不存在，或不是這個人的。兩者不區分。
        Document document = documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);

        return DocumentResponse.from(document);
    }

    /**
     * 更新文件的標題與內文，並偵測編輯衝突。
     *
     * <p><b>兩層防護，防的是兩種不同的情況：</b>
     *
     * <p>① <b>下方的版本比對</b>——防「使用者五分鐘前讀取、現在才送出」。
     * 這是最常見的情況，而 {@code @Version} 擋不住它：
     * 每個 HTTP 請求都重新載入文件，載到的一定是最新版本，
     * 過期的版本號在呼叫端手上，不在我們載入的物件裡。
     *
     * <p>② <b>{@code @Version}（Hibernate 自動）</b>——防「同一毫秒的兩個請求」。
     * 兩個請求都通過了 ① 的比對（都讀到 version=3），
     * 但其中一個先寫入使 version 變成 4，
     * 另一個的 {@code UPDATE ... WHERE version=3} 就會影響 0 列並拋例外。
     * 該例外由 {@code GlobalExceptionHandler} 統一轉成 409。
     *
     * <p>與註冊的兩道防線是同一個模式：
     * 程式檢查負責「給友善的錯誤訊息」，底層機制負責「保證正確」。
     *
     * <p>沒有呼叫 {@code save()}：{@code document} 是這個交易裡載入的受管物件，
     * 交易提交時 Hibernate 會自動送出 UPDATE（dirty checking）。
     *
     * @throws DocumentNotFoundException 文件不存在、已刪除，或不屬於這個使用者
     * @throws DocumentConflictException 呼叫端持有的版本已過期
     */
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

        /*
         * 強制立刻送出 UPDATE，而不是等交易提交。
         *
         * 【為什麼需要這一行】
         *
         * Hibernate 預設把 UPDATE 累積到交易提交時才送出。但有兩個值是
         * 「UPDATE 之後才會確定」的：
         *
         *   version    ← @Version，Hibernate 在 flush 時 +1
         *   updatedAt  ← 資料庫的 trigger 在 UPDATE 時才改
         *
         * 少了這一行，下一行組出來的回應會帶著「更新前」的 version 與 updatedAt。
         *
         * 【具體後果】使用者儲存成功後拿到舊的 version（例如 0），
         * 他馬上再改一次並送回 version=0 → 收到 409「已被別人修改」——
         * 而那個「別人」就是他自己。
         *
         * 【原則】回應裡若包含「由資料庫產生的值」，就必須先 flush 才能讀到。
         */
        documentRepository.flush();

        log.info("文件更新成功 documentId={} userId={} version={}",
                documentId, userId, document.getVersion());

        return DocumentResponse.from(document);
    }

    /**
     * soft delete：標記為已刪除，資料列保留在資料庫中（ADR-005）。
     *
     * <p>刪除之後不需要寫任何額外的判斷，所有查詢就會自動看不到它——
     * 因為每個查詢方法的條件都含 {@code AndDeletedAtIsNull}。
     *
     * <p>因此「已刪除」與「不存在」「不是你的」三種情況自然收斂成同一個結果：404。
     *
     * <p>沒有呼叫 {@code save()}：{@code document} 是這個交易裡載入的受管物件，
     * 交易提交時 Hibernate 的 dirty checking 會自動送出 UPDATE。
     *
     * @throws DocumentNotFoundException 文件不存在、已刪除，或不屬於這個使用者
     */
    @Transactional
    public void delete(Long userId, Long documentId) {

        Document document = documentRepository
                .findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(DocumentNotFoundException::new);

        document.markDeleted();

        log.info("文件刪除成功 documentId={} userId={}", documentId, userId);
    }
}
