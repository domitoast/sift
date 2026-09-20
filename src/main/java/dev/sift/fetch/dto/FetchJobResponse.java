package dev.sift.fetch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.sift.fetch.FailureType;
import dev.sift.fetch.FetchJob;
import dev.sift.fetch.FetchStatus;

import java.time.Instant;

/**
 * Fetch job progress. Polled every couple of seconds until finished is true.
 */
public record FetchJobResponse(
        Long id,
        FetchStatus status,
        Instant startedAt,
        Instant finishedAt,
        int newItemCount,
        FailureType failureType,
        String failureReason
) {
    @JsonProperty("finished")
    // NOTE: @JsonProperty is required. A record serializes its components, and
    // this is a plain method, so without it the field is missing from the JSON.
    public boolean finished() {
        return status == FetchStatus.SUCCESS || status == FetchStatus.FAILED;
    }

    public static FetchJobResponse from(FetchJob job) {
        return new FetchJobResponse(
                job.getId(),
                job.getStatus(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getNewItemCount(),
                job.getFailureType(),
                job.getFailureReason()
        );
    }
}
