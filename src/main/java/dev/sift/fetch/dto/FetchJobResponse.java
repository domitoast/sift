package dev.sift.fetch.dto;

import dev.sift.fetch.FailureType;
import dev.sift.fetch.FetchJob;
import dev.sift.fetch.FetchStatus;

import java.time.Instant;

/**
 * 一次抓取的結果，回給使用者看。
 *
 * <p>刻意不回 {@code sourceId}——它已經在網址裡
 * （{@code GET /sources/{id}/fetch-jobs}），重複回傳沒有意義。
 *
 * @param status         PENDING / RUNNING / SUCCESS / FAILED
 * @param startedAt      開始抓的時間
 * @param finishedAt     結束時間。還在跑的話是 null
 * @param failureType    TRANSIENT（等下次排程）/ PERMANENT（要人去看）。成功時是 null
 * @param failureReason  失敗原因。成功時是 null
 */
public record FetchJobResponse(
        Long id,
        FetchStatus status,
        Instant startedAt,
        Instant finishedAt,
        FailureType failureType,
        String failureReason
) {

    public static FetchJobResponse from(FetchJob job) {
        return new FetchJobResponse(
                job.getId(),
                job.getStatus(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getFailureType(),
                job.getFailureReason()
        );
    }
}
