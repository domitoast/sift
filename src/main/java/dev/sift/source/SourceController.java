package dev.sift.source;

import dev.sift.fetch.dto.FetchedItemResponse;
import dev.sift.fetch.dto.FetchJobResponse;
import dev.sift.source.dto.CreateSourceRequest;
import dev.sift.source.dto.SourceResponse;
import dev.sift.source.dto.UpdateSourceRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Subscription management and manual fetch triggering.
 */
@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {
    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public ResponseEntity<SourceResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateSourceRequest request) {
        SourceResponse response = sourceService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/sources/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<SourceResponse> list(@AuthenticationPrincipal Long userId) {
        return sourceService.findAll(userId);
    }

    @PatchMapping("/{id}")
    public SourceResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSourceRequest request) {
        return sourceService.update(userId, id, request);
    }

    @GetMapping("/{id}/fetch-jobs")
    public List<FetchJobResponse> fetchJobs(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        return sourceService.findFetchJobs(userId, id, Math.clamp(limit, 1, 50));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        sourceService.delete(userId, id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fetch")
    public ResponseEntity<FetchNowResponse> fetchNow(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        Long jobId = sourceService.fetchNow(userId, id);

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/fetch-jobs/" + jobId))
                .body(new FetchNowResponse(jobId));
    }

    public record FetchNowResponse(Long jobId) {
    }

    @GetMapping("/{id}/items")
    public List<FetchedItemResponse> items(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        return sourceService.findItems(userId, id, Math.clamp(limit, 1, 50));
    }
}
