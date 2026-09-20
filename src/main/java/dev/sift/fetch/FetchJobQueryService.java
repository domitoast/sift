package dev.sift.fetch;

import dev.sift.fetch.dto.FetchJobResponse;
import dev.sift.source.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a single job with an ownership check.
 *
 * fetch_job has no user_id column, so ownership cannot be expressed as a query
 * condition the way it is elsewhere; it takes a second lookup through the source.
 */
@Service
public class FetchJobQueryService {
    private final FetchJobRepository fetchJobRepository;
    private final SourceRepository sourceRepository;

    public FetchJobQueryService(FetchJobRepository fetchJobRepository,
                                SourceRepository sourceRepository) {
        this.fetchJobRepository = fetchJobRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional(readOnly = true)
    public FetchJobResponse findOwned(Long userId, Long jobId) {
        FetchJob job = fetchJobRepository.findById(jobId)
                .orElseThrow(FetchJobNotFoundException::new);

        boolean owned = sourceRepository.findById(job.getSourceId())
                .map(source -> source.getUserId().equals(userId))
                .orElse(false);

        if (!owned) {
            throw new FetchJobNotFoundException();
        }

        return FetchJobResponse.from(job);
    }
}
