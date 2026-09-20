package dev.sift.fetch;

import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The fetch pipeline: job card, HTTP client, feed parser, deduplicated storage.
 *
 * Deliberately not transactional. The middle of this flow is a network call that
 * can take ten seconds, and holding a pooled database connection for that long
 * would starve every other request.
 */
@Service
public class FetchService {
    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private final SourceRepository sourceRepository;
    private final FetchJobService fetchJobService;
    private final FetchedItemService fetchedItemService;
    private final FetchClient fetchClient;
    private final FeedParser feedParser;

    public FetchService(SourceRepository sourceRepository,
                        FetchJobService fetchJobService,
                        FetchedItemService fetchedItemService,
                        FetchClient fetchClient,
                        FeedParser feedParser) {
        this.sourceRepository = sourceRepository;
        this.fetchJobService = fetchJobService;
        this.fetchedItemService = fetchedItemService;
        this.fetchClient = fetchClient;
        this.feedParser = feedParser;
    }

    public void fetchAll() {
        List<Source> sources = sourceRepository.findAllByEnabledTrueAndDeletedAtIsNull();

        log.info("=== 抓取開始，來源數={} ===", sources.size());

        for (Source source : sources) {
            try {
                Long jobId = fetchJobService.enqueue(source.getId());

                if (jobId == null) {
                    log.debug("已有進行中的任務，跳過 sourceId={}", source.getId());
                    continue;
                }

                runJob(source.getId(), jobId);
            } catch (Exception e) {
                log.error("處理來源時發生預期外的錯誤 sourceId={}", source.getId(), e);
            }
        }

        log.info("=== 抓取結束 ===");
    }

    // NOTE: takes ids, not entities. This may run on a pool thread, where an
    // entity from another persistence context is both detached and stale.
    // Never throws: an async failure has to be reported as data, not as an exception.
    public int runJob(Long sourceId, Long jobId) {
        Source source = sourceRepository.findById(sourceId).orElse(null);

        if (source == null || source.getDeletedAt() != null) {
            log.info("來源在排隊期間消失，取消任務 jobId={} sourceId={}", jobId, sourceId);
            fetchJobService.fail(jobId, FailureType.PERMANENT, "來源已被刪除");
            return 0;
        }

        fetchJobService.start(jobId);

        try {
            String rawFeed = fetchClient.fetch(source.getUrl());
            List<FetchedArticle> articles = feedParser.parse(rawFeed);

            int newCount = save(source, jobId, articles);

            log.info("來源「{}」取得 {} 篇，其中 {} 篇是新的",
                    source.getName(), articles.size(), newCount);

            fetchJobService.succeed(jobId, newCount);

            return newCount;
        } catch (FeedFetchException e) {
            log.warn("抓取來源失敗 sourceId={} 原因={}", sourceId, e.getMessage());
            fetchJobService.fail(jobId, e.getFailureType(), e.getMessage());
            return 0;
        } catch (FeedParseException e) {
            log.warn("來源回應不是合法的 feed sourceId={} 原因={}", sourceId, e.getMessage());
            fetchJobService.fail(jobId, FailureType.PERMANENT, e.getMessage());
            return 0;
        } catch (Exception e) {
            log.error("抓取時發生預期外的錯誤 sourceId={} jobId={}", sourceId, jobId, e);
            fetchJobService.fail(jobId, FailureType.TRANSIENT, "預期外的錯誤：" + e);
            return 0;
        }
    }

    private int save(Source source, Long jobId, List<FetchedArticle> articles) {
        int newCount = 0;

        for (FetchedArticle article : articles) {
            if (fetchedItemService.saveIfNew(source.getId(), jobId, article)) {
                newCount++;
                log.info("    + {}", article.title());
            }
        }

        return newCount;
    }
}
