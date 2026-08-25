package dev.sift.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * DocumentVersion 的資料存取層。
 *
 * <p><b>注意這裡的方法都「不」帶 userId</b>，與 {@code DocumentRepository} 不同。
 *
 * <p>因為 {@code document_version} 表沒有 user_id 欄位——擁有者由它指向的
 * 那篇文件決定。因此權限檢查必須在 Service 裡先做完（先確認文件是你的），
 * 再呼叫這裡的方法。
 *
 * <p>這是本專案唯一「權限不在查詢條件裡」的地方，屬於刻意的例外。
 */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    /**
     * 列出某篇文件的所有版本，最新的在前。
     *
     * <p>沒有 {@code Pageable}：版本數上限固定為 20，
     * 是「答得出最多幾筆」的清單，因此不需要分頁。
     */
    List<DocumentVersionSummary> findSummariesByDocumentIdOrderByVersionNumberDesc(Long documentId);

    /**
     * 取得某篇文件的指定版本（含內文）。
     */
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    /**
     * 目前最大的版本號。文件還沒有任何版本時回傳 null。
     *
     * <p>下一版的號碼 = 這個值 + 1。
     *
     * <p><b>為什麼不用 {@code document.version} 推算</b>：
     * 那個欄位是 optimistic lock 的計數器，任何 UPDATE 都會讓它 +1，
     * 包含 soft delete。兩者會逐漸對不上。
     *
     * <p><b>併發安全嗎</b>：安全。兩個人同時編輯同一篇文件時，
     * optimistic lock 會擋掉其中一個，因此不會產生重複的 version_number。
     */
    @Query("SELECT MAX(v.versionNumber) FROM DocumentVersion v WHERE v.documentId = :documentId")
    Integer findMaxVersionNumber(@Param("documentId") Long documentId);

    /**
     * 刪除版本號小於等於指定值的所有版本——用來把歷史修剪到上限之內。
     *
     * @return 實際刪除的列數
     */
    @Modifying
    @Query("DELETE FROM DocumentVersion v " +
           "WHERE v.documentId = :documentId AND v.versionNumber <= :maxVersionToDelete")
    int deleteOlderThan(@Param("documentId") Long documentId,
                        @Param("maxVersionToDelete") int maxVersionToDelete);
}
