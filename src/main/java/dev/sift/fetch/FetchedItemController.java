package dev.sift.fetch;

import dev.sift.document.dto.DocumentResponse;
import dev.sift.fetch.dto.FetchedItemDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Reading a fetched article and deciding what to do with it.
 */
@RestController
@RequestMapping("/api/v1/fetched-items")
public class FetchedItemController {
    private final PromoteService promoteService;

    public FetchedItemController(PromoteService promoteService) {
        this.promoteService = promoteService;
    }

    @GetMapping("/{id}")
    public FetchedItemDetailResponse detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return promoteService.findDetail(userId, id);
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<DocumentResponse> promote(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        DocumentResponse document = promoteService.promote(userId, id);

        return ResponseEntity
                .created(URI.create("/api/v1/documents/" + document.id()))
                .body(document);
    }

    @PostMapping("/{id}/resummarize")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resummarize(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        promoteService.resummarize(userId, id);
    }

    @PostMapping("/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        promoteService.discard(userId, id);
    }
}
