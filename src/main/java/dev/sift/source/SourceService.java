package dev.sift.source;

import dev.sift.fetch.FailureType;
import dev.sift.fetch.FeedResolver;
import dev.sift.fetch.FetchAlreadyRunningException;
import dev.sift.fetch.FetchJobRepository;
import dev.sift.fetch.FetchJobService;
import dev.sift.fetch.FetchRunner;
import dev.sift.fetch.FetchedItemRepository;
import dev.sift.fetch.SourceItemCount;
import org.springframework.core.task.TaskRejectedException;
import dev.sift.fetch.dto.FetchJobResponse;
import dev.sift.fetch.dto.FetchedItemResponse;
import dev.sift.source.dto.CreateSourceRequest;
import org.springframework.data.domain.Limit;
import dev.sift.source.dto.SourceResponse;
import dev.sift.source.dto.UpdateSourceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Subscription management.
 *
 * Creating a source validates the URL and then queues a first fetch, so a new
 * subscription is not an empty screen.
 */
@Service
public class SourceService {
    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sourceRepository;
    private final FetchJobRepository fetchJobRepository;
    private final FeedResolver feedResolver;
    private final FetchedItemRepository fetchedItemRepository;
    private final FetchJobService fetchJobService;
    private final FetchRunner fetchRunner;

    public SourceService(SourceRepository sourceRepository,
                         FetchJobRepository fetchJobRepository,
                         FeedResolver feedResolver, FetchedItemRepository fetchedItemRepository,
                         FetchJobService fetchJobService,
                         FetchRunner fetchRunner) {
        this.sourceRepository = sourceRepository;
        this.fetchJobRepository = fetchJobRepository;
        this.feedResolver = feedResolver;
        this.fetchedItemRepository = fetchedItemRepository;
        this.fetchJobService = fetchJobService;
        this.fetchRunner = fetchRunner;
    }

    public Long fetchNow(Long userId, Long sourceId) {
        Source source = sourceRepository.findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        return enqueueFetch(source.getId());
    }

    private Long enqueueFetch(Long sourceId) {
        Long jobId = fetchJobService.enqueue(sourceId);

        if (jobId == null) {
            throw new FetchAlreadyRunningException();
        }

        try {
            fetchRunner.run(sourceId, jobId);
        } catch (TaskRejectedException e) {
            log.warn("抓取佇列已滿，拒絕任務 jobId={} sourceId={}", jobId, sourceId);
            fetchJobService.fail(jobId, FailureType.TRANSIENT, "系統忙碌中，請稍後再試");
            throw e;
        }

        return jobId;
    }

    // NOTE: do not make this @Transactional. The source and the job must be
    // committed before the background thread starts, or it will not find them.
    public SourceResponse create(Long userId, CreateSourceRequest request) {
        String feedUrl = feedResolver.resolve(request.url());

        if (sourceRepository.existsByUrlAndUserIdAndDeletedAtIsNull(feedUrl, userId)) {
            throw new SourceAlreadySubscribedException();
        }

        Source source = new Source(userId, request.name(), feedUrl, request.type());

        try {
            Source saved = sourceRepository.saveAndFlush(source);

            log.info("訂閱來源建立成功 sourceId={} userId={} type={}",
                    saved.getId(), userId, saved.getType());

            Long fetchJobId = null;
            try {
                fetchJobId = enqueueFetch(saved.getId());
            } catch (RuntimeException e) {
                log.warn("首次抓取排隊失敗，來源仍然建立成功 sourceId={} 原因={}",
                        saved.getId(), e.getMessage());
            }

            return SourceResponse.from(saved, fetchJobId);
        } catch (DataIntegrityViolationException e) {
            log.warn("新增來源時發生唯一約束衝突，判定為並發重複訂閱 userId={}", userId);
            throw new SourceAlreadySubscribedException();
        }
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> findAll(Long userId) {
        List<Source> sources =
                sourceRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);

        Map<Long, SourceItemCount> counts = fetchedItemRepository.countByUser(userId)
                .stream()
                .collect(toMap(SourceItemCount::getSourceId, c -> c));

        return sources.stream()
                .map(source -> {
                    SourceItemCount count = counts.get(source.getId());

                    return count == null
                            ? SourceResponse.from(source)
                            : SourceResponse.from(source, count.getTotal(), count.getReady());
                })
                .toList();
    }

    @Transactional
    public SourceResponse update(Long userId, Long sourceId, UpdateSourceRequest request) {
        Source source = sourceRepository
                .findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        if (request.name() != null && !request.name().isBlank()) {
            source.rename(request.name().trim());
        }

        if (request.enabled() != null) {
            source.setEnabled(request.enabled());
        }

        sourceRepository.flush();

        log.info("訂閱來源更新成功 sourceId={} userId={} enabled={}",
                sourceId, userId, source.isEnabled());

        return SourceResponse.from(source);
    }

    @Transactional(readOnly = true)
    public List<FetchJobResponse> findFetchJobs(Long userId, Long sourceId, int limit) {
        sourceRepository.findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        return fetchJobRepository
                .findBySourceIdOrderByCreatedAtDesc(sourceId, Limit.of(limit))
                .stream()
                .map(FetchJobResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long sourceId) {
        Source source = sourceRepository
                .findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        source.markDeleted();

        log.info("訂閱來源刪除成功 sourceId={} userId={}", sourceId, userId);
    }

    @Transactional(readOnly = true)
    public List<FetchedItemResponse> findItems(Long userId, Long sourceId, int limit) {
        sourceRepository.findByIdAndUserIdAndDeletedAtIsNull(sourceId, userId)
                .orElseThrow(SourceNotFoundException::new);

        return fetchedItemRepository
                .findBySourceIdOrderByCreatedAtDescIdDesc(sourceId, Limit.of(limit))
                .stream()
                .map(FetchedItemResponse::from)
                .toList();
    }
}
