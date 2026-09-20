package dev.sift.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sift.document.Document;
import dev.sift.document.DocumentOrigin;
import dev.sift.document.DocumentRepository;
import dev.sift.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PromoteIntegrationTest extends PostgresTestBase {
    private static final String OWNER_EMAIL = "promote-owner@example.com";
    private static final String OTHER_EMAIL = "promote-other@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FetchedItemRepository fetchedItemRepository;

    @Autowired
    private FetchJobRepository fetchJobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @MockitoBean
    private FeedResolver feedResolver;

    @MockitoBean
    private FetchClient fetchClient;

    private static final String EMPTY_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>t</title><link>https://e.com</link>
            <description>d</description></channel></rss>
            """;

    private String ownerToken;
    private String otherToken;
    private long sourceId;
    private long jobId;

    @BeforeEach
    void setUp() throws Exception {
        when(feedResolver.resolve(anyString())).thenAnswer(call -> call.getArgument(0));
        when(fetchClient.fetch(anyString())).thenReturn(EMPTY_RSS);

        ownerToken = registerAndLogin(OWNER_EMAIL);
        otherToken = registerAndLogin(OTHER_EMAIL);

        sourceId = createSource(ownerToken, "測試來源", "https://promote.example.com/rss");
        jobId = fetchJobRepository.save(new FetchJob(sourceId)).getId();
    }

    @Test
    @DisplayName("1. promote 一篇 READY 的文章 → 201，並建立一篇 FETCHED 的 document")
    void promote_shouldCreateDocument() throws Exception {
        FetchedItem item = readyItem("Rust 1.90 發布", "hash-1");

        String body = mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Rust 1.90 發布"))
                .andExpect(jsonPath("$.origin").value("FETCHED"))
                .andReturn().getResponse().getContentAsString();

        long documentId = objectMapper.readTree(body).get("id").asLong();

        Document document = documentRepository.findById(documentId).orElseThrow();

        assertThat(document.getFetchedItemId()).isEqualTo(item.getId());
        assertThat(document.getOrigin()).isEqualTo(DocumentOrigin.FETCHED);
    }

    @Test
    @DisplayName("2. promote 之後，來源那筆變成 PROMOTED 且有 promoted_at")
    void promote_shouldMarkItemPromoted() throws Exception {
        FetchedItem item = readyItem("標記測試", "hash-2");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        FetchedItem reloaded = fetchedItemRepository.findById(item.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(FetchedItemStatus.PROMOTED);

        assertThat(reloaded.getPromotedAt()).isNotNull();
    }

    @Test
    @DisplayName("3. document 的內文是「摘要 + 原文連結」（Day 22 選項 B）")
    void promote_contentShouldBeSummaryPlusLink() throws Exception {
        FetchedItem item = readyItem("內容格式", "hash-3");

        String body = mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String content = objectMapper.readTree(body).get("content").asText();

        assertThat(content)
                .contains("[FAKE]")
                .contains("https://example.com/hash-3");
    }

    @Test
    @DisplayName("4. discard → 204，狀態變 DISCARDED，而且「不會」產生 document")
    void discard_shouldNotCreateDocument() throws Exception {
        FetchedItem item = readyItem("不要這篇", "hash-4");
        long before = documentRepository.count();

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/discard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        FetchedItem reloaded = fetchedItemRepository.findById(item.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(FetchedItemStatus.DISCARDED);

        assertThat(documentRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("5. discard 的那筆「不會」被刪掉——它是一筆『我拒絕過』的紀錄")
    void discard_shouldKeepTheRow() throws Exception {
        FetchedItem item = readyItem("拒絕但保留", "hash-5");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/discard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(fetchedItemRepository.findById(item.getId())).isPresent();
    }

    @Test
    @DisplayName("6. promote 兩次 → 第二次 409（狀態已經不是 READY）")
    void promote_twice_shouldConflict() throws Exception {
        FetchedItem item = readyItem("重複收", "hash-6");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("7. 還沒產生摘要的文章不能 promote → 409")
    void promote_notReady_shouldConflict() throws Exception {
        FetchedItem item = saveItem("還沒摘要", "hash-7");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("8. discard 過的文章不能再 promote → 409")
    void promote_afterDiscard_shouldConflict() throws Exception {
        FetchedItem item = readyItem("先丟再收", "hash-8");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/discard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("9. ★ 應用程式層通過了，唯一索引仍然擋住同一筆被收兩次")
    void uniqueIndex_shouldBlockDuplicateDocument() throws Exception {
        FetchedItem item = readyItem("併發", "hash-9");

        String body = mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long documentId = objectMapper.readTree(body).get("id").asLong();
        Long ownerId = documentRepository.findById(documentId).orElseThrow().getUserId();

        Document duplicate = Document.fromFetchedItem(
                ownerId, item.getId(), "偷塞的重複文件", "內容");

        assertThatThrownBy(() -> documentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("10. promote 別人的文章 → 404（不是 403，不洩漏存在性）")
    void promote_otherUsersItem_shouldReturnNotFound() throws Exception {
        FetchedItem item = readyItem("別人的", "hash-10");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("11. promote 不存在的 id → 404（與上一題完全相同的回應）")
    void promote_unknownId_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/fetched-items/99999999/promote")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("12. 沒帶 token → 401")
    void promote_withoutToken_shouldReturnUnauthorized() throws Exception {
        FetchedItem item = readyItem("沒帶票", "hash-12");

        mockMvc.perform(post("/api/v1/fetched-items/" + item.getId() + "/promote"))
                .andExpect(status().isUnauthorized());
    }

    private FetchedItem saveItem(String title, String hash) {
        FetchedItem item = new FetchedItem(sourceId, jobId, hash,
                new FetchedArticle(title, "https://example.com/" + hash, "內文", Instant.now()));

        return fetchedItemRepository.saveAndFlush(item);
    }

    private FetchedItem readyItem(String title, String hash) {
        FetchedItem item = saveItem(title, hash);
        item.startSummarizing();
        item.summarized("[FAKE] " + title + " 的摘要");

        return fetchedItemRepository.saveAndFlush(item);
    }

    private String registerAndLogin(String email) throws Exception {
        String credentials = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private long createSource(String token, String name, String url) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","url":"%s","type":"RSS"}
                                """.formatted(name, url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }
}
