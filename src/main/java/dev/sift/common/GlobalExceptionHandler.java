package dev.sift.common;

import dev.sift.auth.InvalidCredentialsException;
import dev.sift.user.EmailAlreadyUsedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
