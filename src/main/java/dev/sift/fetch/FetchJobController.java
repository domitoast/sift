package dev.sift.fetch;

import dev.sift.fetch.dto.FetchJobResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Progress of a single fetch job. This is what the client polls after a 202.
 */
@RestController
@RequestMapping("/api/v1/fetch-jobs")
public class FetchJobController {
    private final FetchJobQueryService fetchJobQueryService;

    public FetchJobController(FetchJobQueryService fetchJobQueryService) {
        this.fetchJobQueryService = fetchJobQueryService;
    }

    @GetMapping("/{id}")
    public FetchJobResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return fetchJobQueryService.findOwned(userId, id);
    }
}
