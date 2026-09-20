package dev.sift.common;

import dev.sift.auth.InvalidCredentialsException;
import dev.sift.auth.InvalidRefreshTokenException;
import dev.sift.auth.RefreshTokenReuseException;
import dev.sift.document.DocumentConflictException;
import dev.sift.document.DocumentNotFoundException;
import dev.sift.fetch.FeedFetchException;
import dev.sift.fetch.FeedNotFoundException;
import dev.sift.fetch.FetchAlreadyRunningException;
import dev.sift.fetch.FetchJobNotFoundException;
import dev.sift.fetch.FetchedItemNotFoundException;
import dev.sift.fetch.IllegalFetchedItemTransitionException;
import dev.sift.source.SourceAlreadySubscribedException;
import dev.sift.source.SourceNotFoundException;
import dev.sift.user.EmailAlreadyUsedException;
import dev.sift.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;

/**
 * Translates application exceptions into RFC 7807 Problem Details.
 * Keeping this in one place is what makes the error format consistent across every endpoint.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ERROR_TYPE_BASE = "https://sift.dev/errors/";

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

    @ExceptionHandler(FetchedItemNotFoundException.class)
    public ProblemDetail handleFetchedItemNotFound(FetchedItemNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "fetched-item-not-found"));
        problem.setTitle("文章不存在");

        return problem;
    }

    @ExceptionHandler(FetchJobNotFoundException.class)
    public ProblemDetail handleFetchJobNotFound(FetchJobNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "fetch-job-not-found"));
        problem.setTitle("抓取任務不存在");

        return problem;
    }

    @ExceptionHandler(TaskRejectedException.class)
    public ResponseEntity<ProblemDetail> handleTaskRejected(TaskRejectedException e) {
        log.warn("背景工作佇列已滿，拒絕請求", e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "系統正忙，請稍後再試"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "task-rejected"));
        problem.setTitle("系統忙碌");

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(problem);
    }

    @ExceptionHandler(FetchAlreadyRunningException.class)
    public ProblemDetail handleFetchAlreadyRunning(FetchAlreadyRunningException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "fetch-already-running"));
        problem.setTitle("抓取進行中");

        return problem;
    }

    @ExceptionHandler(IllegalFetchedItemTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalFetchedItemTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "illegal-state-transition"));
        problem.setTitle("狀態不允許這個操作");

        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("撞到資料庫約束", e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "這個操作與現有資料衝突，可能已經被處理過了"
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "data-conflict"));
        problem.setTitle("資料衝突");

        return problem;
    }

    @ExceptionHandler(SourceAlreadySubscribedException.class)
    public ProblemDetail handleSourceAlreadySubscribed(SourceAlreadySubscribedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "source-already-subscribed"));
        problem.setTitle("來源已訂閱");

        return problem;
    }

    @ExceptionHandler(FeedNotFoundException.class)
    public ProblemDetail handleFeedNotFound(FeedNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "feed-not-found"));
        problem.setTitle("找不到 feed");

        return problem;
    }

    @ExceptionHandler(FeedFetchException.class)
    public ProblemDetail handleFeedFetch(FeedFetchException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "無法讀取這個網址：" + e.getMessage()
        );
        problem.setType(URI.create(ERROR_TYPE_BASE + "feed-fetch-failed"));
        problem.setTitle("網址無法讀取");

        return problem;
    }

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

    public record FieldError(String field, String message) {
    }
}
