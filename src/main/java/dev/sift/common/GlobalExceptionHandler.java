package dev.sift.common;

import dev.sift.auth.InvalidCredentialsException;
import dev.sift.auth.InvalidRefreshTokenException;
import dev.sift.auth.RefreshTokenReuseException;
import dev.sift.document.DocumentConflictException;
import dev.sift.document.DocumentNotFoundException;
import dev.sift.source.SourceNotFoundException;
import dev.sift.user.EmailAlreadyUsedException;
import dev.sift.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;

/**
 * 全域例外處理器。
 *
 * <p>{@code @RestControllerAdvice} 的意思是：
 * 「所有 Controller 丟出的例外，都先送來這裡處理。」
 *
 * <p><b>為什麼需要它</b>：
 * 若沒有這個類別，每個 Controller 方法都要自己寫 try-catch，
 * 而且每個人的錯誤格式會不一樣。前端必須為每支 API 寫專屬的錯誤處理。
 *
 * <p>有了它，錯誤處理集中在一處，格式統一，Controller 保持乾淨。
 *
 * <p>回應格式採 <b>RFC 7807 Problem Details</b>。
 * Spring 6 內建 {@link ProblemDetail} 類別，不需要自己定義。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_TYPE_BASE = "https://sift.dev/errors/";

    /**
     * email 重複 → 409 Conflict。
     *
     * <p>為什麼是 409 而不是 400：
     * 400 表示「你的請求格式有問題」，但這個請求格式完全正確，
     * 只是與伺服器目前的狀態衝突。409 Conflict 正是這個語意。
     */
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "email-already-used"));
        problem.setTitle("email 已被註冊");

        return problem;
    }

    /**
     * 登入失敗 → 401 Unauthorized。
     *
     * <p>401 的語意是「你是誰？」——身分無法確認。
     * （403 是「我知道你是誰，但你不能碰」，那是權限問題，不是這裡。）
     *
     * <p>⚠️ 訊息刻意含糊，不透露是帳號不存在還是密碼錯誤，
     * 以避免 user enumeration（帳號列舉）攻擊。
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "invalid-credentials"));
        problem.setTitle("登入失敗");

        return problem;
    }

    /**
     * refresh token 無效 → 401 Unauthorized。
     *
     * <p>{@link RefreshTokenReuseException}（偵測到盜用）繼承自這個例外，
     * 因此會被同一個處理器接住——<b>這是刻意的</b>。
     *
     * <p>若盜用回應不同的訊息或狀態碼，攻擊者就能靠回應內容判斷
     * 「我拿到的是一張曾經真實存在的 token」，等於確認了他的攻擊方向正確。
     *
     * <p>兩者的差異只記在伺服器日誌：盜用是 warn，一般無效是 debug。
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "invalid-refresh-token"));
        problem.setTitle("憑證無效");

        return problem;
    }

    /**
     * 使用者不存在或帳號已刪除 → 401 Unauthorized。
     *
     * <p><b>為什麼是 401 而不是 404</b>：
     * 走到這裡代表 token 通過了簽章驗證，但它指向的帳號已經不存在。
     * 也就是說——<b>這張憑證本身已經失效了</b>，
     * 而「憑證無效」正是 401 的語意。
     *
     * <p>404 的語意是「你要找的東西不在」，但使用者要找的是「他自己」；
     * 真正的問題不是資源找不到，是他的身分已經不成立。
     *
     * <p>⚠️ <b>已知的不一致</b>：這個 401 帶有 JSON body，
     * 而 filter chain 擋下的 401（沒帶 token、token 過期）body 是空的。
     * 兩者狀態碼相同，前端行為一致，因此可接受。
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "user-not-found"));
        problem.setTitle("帳號已失效");

        return problem;
    }

    /**
     * 輸入驗證失敗 → 400 Bad Request。
     *
     * <p>當 {@code @Valid} 檢查不通過時，Spring 會丟出
     * {@code MethodArgumentNotValidException}，裡面裝著所有失敗的欄位。
     *
     * <p>此處把它整理成 {@code errors} 陣列，讓前端能精確標示
     * 「是哪個欄位錯了、錯在哪」，而不是只給一句籠統的「輸入有誤」。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException e) {

        List<FieldError> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "輸入資料未通過驗證"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "validation-failed"));
        problem.setTitle("輸入驗證失敗");
        problem.setProperty("errors", errors);

        return problem;
    }

    /**
     * 編輯衝突 → 409 Conflict。
     *
     * <p>由 Service 明確比對版本號後拋出——防「使用者讀取後隔了一段時間才送出」。
     *
     * <p>{@code setProperty} 在標準的 Problem Details 欄位之外多加一個自訂欄位，
     * 讓呼叫端知道目前的版本是多少，可據此決定要不要重新載入。
     */
    @ExceptionHandler(DocumentConflictException.class)
    public ProblemDetail handleDocumentConflict(DocumentConflictException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "document-conflict"));
        problem.setTitle("編輯衝突");
        problem.setProperty("currentVersion", e.getCurrentVersion());

        return problem;
    }

    /**
     * Hibernate 偵測到的 optimistic lock 失敗 → 409 Conflict。
     *
     * <p>與上一個處理器是<b>不同的觸發時機</b>：
     * 這個來自「兩個請求同時進行」——雙方都通過了 Service 的版本比對，
     * 但其中一方先寫入，另一方的 {@code UPDATE ... WHERE version=?} 影響 0 列。
     *
     * <p><b>為什麼不在 Service 裡 catch</b>：Hibernate 是在交易「提交時」
     * 才送出 UPDATE 並發現衝突，那個時間點已經在 Service 方法之外了。
     * 因此只能在這一層處理。
     *
     * <p>對外的回應與上一個相同——呼叫端不需要知道是哪一層偵測到的。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockFailure(OptimisticLockingFailureException e) {

        log.warn("optimistic lock 衝突（並發寫入）：{}", e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "這篇文件已被修改，請重新載入後再編輯"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "document-conflict"));
        problem.setTitle("編輯衝突");

        return problem;
    }

    /**
     * 文件不存在、已刪除，或不屬於這個使用者 → 404 Not Found。
     *
     * <p>三種情況共用同一個回應是刻意的：
     * 若「別人的文件」回 403、「不存在」回 404，
     * 呼叫端就能靠狀態碼差異列舉出哪些 id 真的存在（ADR-006）。
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleDocumentNotFound(DocumentNotFoundException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "document-not-found"));
        problem.setTitle("文件不存在");

        return problem;
    }

    /**
     * 訂閱來源不存在、已刪除，或不屬於這個使用者 → 404 Not Found。
     */
    @ExceptionHandler(SourceNotFoundException.class)
    public ProblemDetail handleSourceNotFound(SourceNotFoundException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "source-not-found"));
        problem.setTitle("訂閱來源不存在");

        return problem;
    }

    /**
     * 路徑不存在 → 404 Not Found。
     *
     * <p>Spring 找不到對應的 handler 時會丟出 {@code NoResourceFoundException}。
     *
     * <p><b>為什麼需要明確處理它</b>：
     * 這個例外也是 {@code Exception} 的子類別，若沒有這個 handler，
     * 下方的 catch-all 會接住它並回傳 <b>500</b>——
     * 使用者只是打錯網址，卻被告知「系統發生非預期錯誤」。
     *
     * <p>後果不只是訊息不精確：前端會以為伺服器故障而開始重試，
     * 而每一次都會寫進 {@code log.error}，把真正的錯誤淹沒在雜訊裡。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "找不到請求的資源"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "not-found"));
        problem.setTitle("資源不存在");

        return problem;
    }

    /**
     * HTTP method 不支援 → 405 Method Not Allowed。
     *
     * <p>例如對只接受 POST 的 {@code /auth/login} 送 GET。
     * 與上一個 handler 同理，若不處理會被誤報為 500。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "此路徑不支援 " + e.getMethod() + " 方法"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "method-not-allowed"));
        problem.setTitle("方法不允許");

        return problem;
    }

    /**
     * 請求內容無法解析 → 400 Bad Request。
     *
     * <p>典型情境：送進來的 JSON 少了結尾的大括號，或欄位型別對不上。
     *
     * <p>注意這與 {@code MethodArgumentNotValidException} 不同：
     * 那個是「JSON 讀得懂，但內容不合規則」，
     * 這個是「JSON 根本讀不懂」，連轉成物件都做不到。
     *
     * <p>⚠️ 刻意不回傳 {@code e.getMessage()}——Jackson 的解析錯誤訊息
     * 會包含類別名稱與套件路徑，屬於內部結構資訊。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException e) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "請求內容格式無法解析"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "malformed-request"));
        problem.setTitle("請求格式錯誤");

        return problem;
    }

    /**
     * 未預期的例外 → 500 Internal Server Error。
     *
     * <p><b>這個處理器有兩個關鍵設計</b>：
     *
     * <p>一、<b>完整堆疊寫進日誌，但不回傳給客戶端</b>。
     * 堆疊追蹤會洩漏套件結構、函式庫版本、甚至 SQL 語句，
     * 是攻擊者最喜歡的資訊來源。
     *
     * <p>二、回傳給客戶端的訊息刻意含糊。
     * 使用者不需要知道是 NullPointerException 還是連線逾時，
     * 他只需要知道「這是我們的問題，不是你的」。
     *
     * <p>⚠️ 這個 handler 必須存在。少了它，Spring 的預設行為
     * 在某些設定下會把堆疊追蹤直接吐給客戶端。
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {

        log.error("未預期的例外", e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "系統發生非預期錯誤，請稍後再試"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "internal-error"));
        problem.setTitle("系統錯誤");

        return problem;
    }

    /**
     * 單一欄位的驗證錯誤。
     *
     * @param field   欄位名稱
     * @param message 錯誤訊息
     */
    public record FieldError(String field, String message) {
    }
}
