package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guizhou.wudong.api.ApiRequestException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class V3KnowledgeService {
    private static final Set<String> READ_STATUSES = Set.of("UNREVIEWED", "SEARCH_SNIPPET_ONLY",
            "FULL_TEXT_REVIEWED", "ARCHIVED_COPY_REVIEWED");
    private static final Set<String> REVIEWED_STATUSES = Set.of("FULL_TEXT_REVIEWED", "ARCHIVED_COPY_REVIEWED");
    private static final Set<String> RUN_STATES = Set.of("RUNNING", "CANCEL_REQUESTED", "STOPPED",
            "INTERRUPTED", "COMPLETED", "FAILED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final String retrievalMode;
    private final int maxResults;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wudong-knowledge-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public V3KnowledgeService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager,
                              @Value("${wudong.knowledge.retrieval-mode:KEYWORD_DEMO}") String retrievalMode,
                              @Value("${wudong.knowledge.max-results:10}") int maxResults) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.retrievalMode = retrievalMode;
        this.maxResults = maxResults;
    }

    @PreDestroy
    void closeExecutor() {
        executor.shutdown();
    }

    public List<Map<String, Object>> sources() {
        return jdbc.queryForList("SELECT * FROM knowledge_source ORDER BY source_key,id").stream()
                .map(this::sourceView).toList();
    }

    public Map<String, Object> source(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM knowledge_source WHERE id=?", id)
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return sourceView(row);
    }

    public Map<String, Object> createSource(Map<String, Object> body) {
        SourceValue value = sourceValue(body, false);
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO knowledge_source(
                      id,source_version,source_key,title,url,publisher,source_kind,publication_date_text,
                      read_at,locator,read_status)
                    VALUES (?,1,?,?,?,?,?,?,?,?,?)
                    """, id, value.sourceKey(), value.title(), value.url(), value.publisher(), value.sourceKind(),
                    value.publicationDateText(), value.readAt(), value.locator(), value.readStatus());
        } catch (DuplicateKeyException exception) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "SOURCE_KEY_ALREADY_EXISTS", "来源编号已存在");
        }
        return source(id);
    }

    public Map<String, Object> updateSource(String id, Map<String, Object> body) {
        V3Support.uuid(id, "id");
        SourceValue value = sourceValue(body, true);
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        try {
            int updated = jdbc.update("""
                    UPDATE knowledge_source SET source_key=?,title=?,url=?,publisher=?,source_kind=?,
                      publication_date_text=?,read_at=?,locator=?,read_status=?,source_version=source_version+1
                    WHERE id=? AND source_version=?
                    """, value.sourceKey(), value.title(), value.url(), value.publisher(), value.sourceKind(),
                    value.publicationDateText(), value.readAt(), value.locator(), value.readStatus(), id, expectedVersion);
            if (updated != 1) {
                Map<String, Object> current = source(id);
                throw versionConflict(expectedVersion, ((Number) current.get("version")).intValue());
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "SOURCE_KEY_ALREADY_EXISTS", "来源编号已存在");
        }
        return source(id);
    }

    public List<Map<String, Object>> adminDocuments() {
        return jdbc.queryForList("SELECT * FROM knowledge_document ORDER BY updated_at DESC,id DESC")
                .stream().map(this::adminView).toList();
    }

    public Map<String, Object> adminDocument(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = documentRow(id, false);
        if (row == null) {
            V3Support.notFound();
        }
        return adminView(row);
    }

    public Map<String, Object> createDocument(Map<String, Object> body) {
        V3Support.onlyKeys(body, "candidateCode", "draft");
        requirePresent(body, "candidateCode", "draft");
        String candidateCode = candidateCode(body.get("candidateCode"));
        Map<String, Object> draft = resolvedDraft(V3Support.object(body, "draft"));
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO knowledge_document(
                      id,candidate_code,title,content,source_type,tags,published,index_status,demo_data,
                      row_version,draft_revision,visibility,draft_json)
                    VALUES (?,?,?,?,?,?,false,'DRAFT',?,1,1,'DRAFT',?)
                    """, id, candidateCode, draft.get("title"), draft.get("content"), "V3_MANAGED",
                    String.join(",", strings(draft.get("tags"))), draft.get("demoData"), json(draft));
        } catch (DuplicateKeyException exception) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "CANDIDATE_CODE_ALREADY_EXISTS", "候选编号已存在");
        }
        return adminDocument(id);
    }

    public Map<String, Object> updateDocument(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion", "candidateCode", "draft");
        requirePresent(body, "expectedVersion", "candidateCode", "draft");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        String candidateCode = candidateCode(body.get("candidateCode"));
        Map<String, Object> draft = resolvedDraft(V3Support.object(body, "draft"));
        try {
            return transaction.execute(status -> {
                Map<String, Object> current = lockedDocument(id);
                int currentVersion = number(current.get("row_version"));
                if (currentVersion != expectedVersion) {
                    throw versionConflict(expectedVersion, currentVersion);
                }
                if (current.get("current_task_id") != null) {
                    Map<String, Object> task = jdbc.queryForMap("SELECT id,status FROM knowledge_publish_task WHERE id=?",
                            current.get("current_task_id"));
                    throw new ApiRequestException(HttpStatus.CONFLICT, "PUBLISH_IN_PROGRESS", "知识正在发布",
                            V3Support.map("kind", "publish_in_progress", "taskId", task.get("id"),
                                    "status", task.get("status")));
                }
                String currentDraft = current.get("draft_json") == null ? null
                        : V3Support.canonicalJson(readJson(current.get("draft_json")));
                boolean draftChanged = !V3Support.canonicalJson(draft).equals(currentDraft);
                boolean codeChanged = !java.util.Objects.equals(candidateCode, current.get("candidate_code"));
                if (!draftChanged && !codeChanged) {
                    return adminView(current);
                }
                int updated = jdbc.update("""
                        UPDATE knowledge_document SET candidate_code=?,title=?,content=?,tags=?,demo_data=?,
                          draft_json=?,row_version=row_version+1,draft_revision=draft_revision+?
                        WHERE id=? AND row_version=? AND current_task_id IS NULL
                        """, candidateCode, draft.get("title"), draft.get("content"),
                        String.join(",", strings(draft.get("tags"))), draft.get("demoData"), json(draft),
                        draftChanged ? 1 : 0, id, expectedVersion);
                if (updated != 1) {
                    throw publishUnavailable();
                }
                return adminDocument(id);
            });
        } catch (DuplicateKeyException exception) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "CANDIDATE_CODE_ALREADY_EXISTS", "候选编号已存在");
        }
    }

    public PublishResult publish(String adminId, String documentId, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        V3Support.uuid(documentId, "id");
        V3Support.uuid(requestKey, "Idempotency-Key");
        String requestHash = V3Support.sha256(V3Support.map("contractVersion", V3Support.CONTRACT,
                "operationType", "PUBLISH_KNOWLEDGE", "accountId", adminId, "requestKey", requestKey,
                "documentId", documentId, "expectedVersion", expectedVersion));
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> replay = transaction.execute(status -> {
            try {
                jdbc.update("""
                        INSERT INTO knowledge_publish_task(
                          id,document_id,requested_by,request_key,request_hash,status,deadline_at)
                        VALUES (?,?,?,?,?,'RESERVED',DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 180 SECOND))
                        """, taskId, documentId, adminId, requestKey, requestHash);
                return null;
            } catch (DuplicateKeyException exception) {
                Map<String, Object> existing = jdbc.queryForMap("""
                        SELECT * FROM knowledge_publish_task WHERE requested_by=? AND request_key=?
                        """, adminId, requestKey);
                if (!requestHash.equals(existing.get("request_hash"))) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "请求键已绑定其他发布请求");
                }
                return existing;
            }
        });
        if (replay != null) {
            return new PublishResult(V3Support.map("task", taskView(replay), "replayed", true), true);
        }
        PublishStage stage = transaction.execute(status -> freezePublication(taskId, documentId, expectedVersion));
        if (stage == null) {
            throw publishUnavailable();
        }
        if (stage.errorCode() != null) {
            throw new ApiRequestException(stage.errorStatus(), stage.errorCode(), stage.errorMessage(), stage.details());
        }
        executor.submit(() -> completeKeyword(taskId));
        return new PublishResult(V3Support.map("taskId", taskId, "status", "PENDING",
                "documentVersion", stage.documentVersion(), "editable", false, "replayed", false), false);
    }

    public List<Map<String, Object>> tasks(String documentId) {
        if (documentId == null) {
            return jdbc.queryForList("SELECT * FROM knowledge_publish_task ORDER BY created_at DESC,id DESC")
                    .stream().map(this::taskView).toList();
        }
        V3Support.uuid(documentId, "documentId");
        return jdbc.queryForList("""
                SELECT * FROM knowledge_publish_task WHERE document_id=? ORDER BY created_at DESC,id DESC
                """, documentId).stream().map(this::taskView).toList();
    }

    public Map<String, Object> task(String taskId) {
        V3Support.uuid(taskId, "taskId");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM knowledge_publish_task WHERE id=?", taskId)
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return taskView(row);
    }

    public Map<String, Object> unpublish(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        return transaction.execute(status -> {
            Map<String, Object> current = lockedDocument(id);
            int version = number(current.get("row_version"));
            if (version != expectedVersion) {
                throw versionConflict(expectedVersion, version);
            }
            if ("WITHDRAWN".equals(current.get("visibility"))) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "KNOWLEDGE_ALREADY_WITHDRAWN", "知识已经下架");
            }
            Object currentTask = current.get("current_task_id");
            if (currentTask != null) {
                jdbc.update("""
                        UPDATE knowledge_publish_task SET status='INVALIDATED',finished_at=CURRENT_TIMESTAMP
                        WHERE id=? AND status IN ('PENDING','RUNNING')
                        """, currentTask);
            }
            int updated = jdbc.update("""
                    UPDATE knowledge_document SET visibility='WITHDRAWN',current_task_id=NULL,row_version=row_version+1
                    WHERE id=? AND row_version=?
                    """, id, expectedVersion);
            if (updated != 1) {
                throw publishUnavailable();
            }
            return adminDocument(id);
        });
    }

    public List<Map<String, Object>> publicDocuments(String keyword, List<String> tags) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && (normalizedKeyword.isEmpty()
                || normalizedKeyword.codePointCount(0, normalizedKeyword.length()) > 200)) {
            V3Support.bad("keyword 长度不符合要求");
        }
        return eligibleRows().stream().map(this::publicView)
                .filter(view -> normalizedKeyword == null || V3Support.canonicalJson(view).contains(normalizedKeyword))
                .filter(view -> strings(view.get("tags")).containsAll(tags == null ? List.of() : tags)).toList();
    }

    public Map<String, Object> publicDocument(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = eligibleRows().stream().filter(value -> id.equals(value.get("id")))
                .findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return publicView(row);
    }

    public Map<String, Object> keywordSearch(String keywords, Integer requestedLimit) {
        if (!"KEYWORD_DEMO".equals(retrievalMode)) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "RETRIEVAL_MODE_MISMATCH", "当前未启用关键词演示检索");
        }
        if (keywords == null || keywords.trim().isEmpty()
                || keywords.trim().codePointCount(0, keywords.trim().length()) > 200) {
            V3Support.bad("keywords 长度不符合要求");
        }
        int limit = requestedLimit == null ? Math.min(maxResults, 10) : requestedLimit;
        if (limit < 1 || limit > 10) {
            V3Support.bad("limit 必须在 1 到 10 之间");
        }
        List<String> tokens = keywordTokens(keywords);
        List<ScoredEvidence> scored = new ArrayList<>();
        for (Map<String, Object> row : eligibleRows()) {
            Map<String, Object> snapshot = readJson(row.get("live_snapshot"));
            String title = normalize(snapshot.get("title").toString());
            String content = normalize(snapshot.get("content").toString());
            List<String> tags = strings(snapshot.get("tags")).stream().map(V3KnowledgeService::normalize).toList();
            int score = 0;
            int titleHits = 0;
            for (String token : tokens.stream().distinct().toList()) {
                if (title.contains(token)) {
                    score += 3;
                    titleHits++;
                }
                if (tags.stream().anyMatch(tag -> tag.contains(token))) {
                    score += 2;
                }
                if (content.contains(token)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredEvidence(row, evidence(row), score, titleHits));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredEvidence::score).reversed()
                .thenComparing(Comparator.comparingInt(ScoredEvidence::titleHits).reversed())
                .thenComparing(value -> value.row().get("published_at").toString(), Comparator.reverseOrder())
                .thenComparing(value -> value.row().get("id").toString()));
        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, scored.size()); index++) {
            ScoredEvidence value = scored.get(index);
            results.add(V3Support.map("rank", index + 1, "documentId", value.row().get("id"),
                    "buildId", value.row().get("active_task_id"), "evidence", value.evidence()));
        }
        return V3Support.map("retrievalMode", "KEYWORD_DEMO", "configHash", keywordConfigHash(),
                "checkedAt", V3Support.now(), "results", results);
    }

    public Map<String, Object> activeBuilds(String configHash) {
        if (configHash == null || !configHash.matches("^sha256:[0-9a-f]{64}$")) {
            V3Support.bad("configHash 无效");
        }
        List<Map<String, Object>> builds = jdbc.queryForList("""
                SELECT d.id document_id,d.active_task_id build_id,d.live_snapshot_hash snapshot_hash
                FROM knowledge_document d JOIN knowledge_publish_task t ON t.id=d.active_task_id
                WHERE d.visibility='PUBLISHED' AND d.live_snapshot IS NOT NULL
                  AND t.status='SUCCEEDED' AND t.retrieval_mode='VECTOR' AND t.config_hash=?
                  AND t.snapshot_hash=d.live_snapshot_hash ORDER BY d.id
                """, configHash).stream().map(row -> (Map<String, Object>) V3Support.map(
                        "documentId", row.get("document_id"), "buildId", row.get("build_id"),
                        "snapshotHash", row.get("snapshot_hash"))).toList();
        return V3Support.map("retrievalMode", "VECTOR", "configHash", configHash,
                "checkedAt", V3Support.now(), "builds", builds);
    }

    public Map<String, Object> eligibility(Map<String, Object> body) {
        V3Support.onlyKeys(body, "retrievalMode", "configHash", "candidates");
        String mode = V3Support.rawRequiredText(body, "retrievalMode");
        if (!Set.of("KEYWORD_DEMO", "VECTOR").contains(mode)) {
            V3Support.bad("retrievalMode 无效");
        }
        String configHash = V3Support.digest(body, "configHash");
        if ("KEYWORD_DEMO".equals(mode) && !keywordConfigHash().equals(configHash)) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "RETRIEVAL_MODE_MISMATCH", "关键词配置摘要不匹配");
        }
        List<Map<String, Object>> candidates = V3Support.objectList(body, "candidates", 1, 50);
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (Map<String, Object> candidate : candidates) {
                V3Support.onlyKeys(candidate, "documentId", "buildId");
                String documentId = V3Support.uuid(candidate, "documentId");
                String buildId = V3Support.uuid(candidate, "buildId");
                if (!seen.add(documentId + ":" + buildId)) {
                    V3Support.bad("candidates 不能重复");
                }
                Map<String, Object> row = eligibleRows().stream()
                        .filter(value -> documentId.equals(value.get("id")) && buildId.equals(value.get("active_task_id")))
                        .findFirst().orElse(null);
                boolean eligible = row != null && ("KEYWORD_DEMO".equals(mode)
                        || "VECTOR".equals(row.get("retrieval_mode")) && configHash.equals(row.get("config_hash")));
                LinkedHashMap<String, Object> result = V3Support.map("documentId", documentId,
                        "buildId", buildId, "eligible", eligible);
                if (eligible) {
                    result.put("evidence", evidence(row));
                }
                results.add(result);
            }
        } catch (DataAccessException exception) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_ELIGIBILITY_UNAVAILABLE",
                    "知识资格暂时无法确认");
        }
        return V3Support.map("retrievalMode", mode, "configHash", configHash,
                "checkedAt", V3Support.now(), "results", results);
    }

    public Map<String, Object> buildInputs(String taskId) {
        V3Support.uuid(taskId, "taskId");
        Map<String, Object> row = jdbc.queryForList("""
                SELECT t.*,d.current_task_id FROM knowledge_publish_task t
                JOIN knowledge_document d ON d.id=t.document_id WHERE t.id=?
                """, taskId).stream().findFirst().orElse(null);
        if (row == null || !"RUNNING".equals(row.get("status")) || !taskId.equals(row.get("current_task_id"))
                || row.get("input_snapshot") == null || row.get("index_config") == null
                || !Instant.now().isBefore(timestamp(row.get("deadline_at")))) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "BUILD_TASK_NOT_ELIGIBLE", "构建任务当前不具资格");
        }
        return V3Support.map("taskId", taskId, "documentId", row.get("document_id"),
                "draftRevision", row.get("draft_revision"), "snapshotHash", row.get("snapshot_hash"),
                "snapshot", readJson(row.get("input_snapshot")), "indexConfig", readJson(row.get("index_config")),
                "configHash", row.get("config_hash"), "deadlineAt", V3Support.utc(row.get("deadline_at")));
    }

    public Map<String, Object> recordRunSummary(Map<String, Object> body) {
        ValidatedSummary summary = validateSummary(body);
        String digest = V3Support.sha256(body);
        String receivedAt = V3Support.now();
        try {
            return transaction.execute(status -> {
                Map<String, Object> current = jdbc.queryForList("""
                        SELECT * FROM agent_run_summary_v3 WHERE thread_id=? AND run_id=? FOR UPDATE
                        """, summary.threadId(), summary.runId()).stream().findFirst().orElse(null);
                if (current == null) {
                    jdbc.update("""
                            INSERT INTO agent_run_summary_v3(
                              thread_id,run_id,last_sequence,last_digest,run_state,agent_type,retrieval_mode,
                              started_at,finished_at,knowledge_dependencies_json,candidate_refs_json,error_code)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                            """, summary.threadId(), summary.runId(), summary.sequence(), digest, summary.runState(),
                            summary.agentType(), summary.retrievalMode(), Timestamp.from(summary.startedAt()),
                            summary.finishedAt() == null ? null : Timestamp.from(summary.finishedAt()),
                            json(summary.dependencies()), json(summary.candidateRefs()), summary.errorCode());
                    return runSummaryResult(summary, summary.sequence(), summary.runState(), "APPLIED", receivedAt);
                }
                int storedSequence = number(current.get("last_sequence"));
                if (summary.sequence() < storedSequence) {
                    return runSummaryResult(summary, storedSequence, current.get("run_state").toString(),
                            "STALE_IGNORED", receivedAt);
                }
                if (summary.sequence() == storedSequence) {
                    if (!digest.equals(current.get("last_digest"))) {
                        throw new ApiRequestException(HttpStatus.CONFLICT, "RUN_SUMMARY_SEQUENCE_CONFLICT",
                                "同一摘要序号已绑定不同内容");
                    }
                    return runSummaryResult(summary, storedSequence, current.get("run_state").toString(),
                            "REPLAYED", receivedAt);
                }
                String currentState = current.get("run_state").toString();
                if (V3Support.terminalRunState(currentState)) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "RUN_SUMMARY_TERMINAL", "运行摘要已经终结");
                }
                if (!validRunTransition(currentState, summary.runState())) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "RUN_SUMMARY_SEQUENCE_CONFLICT",
                            "运行摘要状态迁移无效");
                }
                int updated = jdbc.update("""
                        UPDATE agent_run_summary_v3 SET last_sequence=?,last_digest=?,run_state=?,agent_type=?,
                          retrieval_mode=?,started_at=?,finished_at=?,knowledge_dependencies_json=?,
                          candidate_refs_json=?,error_code=?,received_at=CURRENT_TIMESTAMP
                        WHERE thread_id=? AND run_id=? AND last_sequence<?
                        """, summary.sequence(), digest, summary.runState(), summary.agentType(), summary.retrievalMode(),
                        Timestamp.from(summary.startedAt()), summary.finishedAt() == null ? null : Timestamp.from(summary.finishedAt()),
                        json(summary.dependencies()), json(summary.candidateRefs()), summary.errorCode(),
                        summary.threadId(), summary.runId(), summary.sequence());
                if (updated != 1) {
                    throw runUnavailable();
                }
                return runSummaryResult(summary, summary.sequence(), summary.runState(), "APPLIED", receivedAt);
            });
        } catch (DataAccessException exception) {
            throw runUnavailable();
        }
    }

    private PublishStage freezePublication(String taskId, String documentId, int expectedVersion) {
        Map<String, Object> document = lockedDocument(documentId);
        Map<String, Object> task = jdbc.queryForMap("SELECT * FROM knowledge_publish_task WHERE id=? FOR UPDATE", taskId);
        int currentVersion = number(document.get("row_version"));
        if (currentVersion != expectedVersion) {
            failTask(taskId, "VERSION_CONFLICT", "知识版本已变化");
            return PublishStage.error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "知识版本已变化",
                    V3Support.map("kind", "version_conflict", "expectedVersion", expectedVersion,
                            "currentVersion", currentVersion));
        }
        if (document.get("current_task_id") != null) {
            Map<String, Object> current = jdbc.queryForMap("SELECT id,status FROM knowledge_publish_task WHERE id=?",
                    document.get("current_task_id"));
            failTask(taskId, "PUBLISH_IN_PROGRESS", "知识正在发布");
            return PublishStage.error(HttpStatus.CONFLICT, "PUBLISH_IN_PROGRESS", "知识正在发布",
                    V3Support.map("kind", "publish_in_progress", "taskId", current.get("id"),
                            "status", current.get("status")));
        }
        if (!"RESERVED".equals(task.get("status"))) {
            return PublishStage.error(HttpStatus.SERVICE_UNAVAILABLE, "PUBLISH_RESULT_UNAVAILABLE",
                    "发布结果暂时无法确认", null);
        }
        Map<String, Object> draft = readJson(document.get("draft_json"));
        List<String> missing = publicationMissing(draft);
        if (!missing.isEmpty()) {
            failTask(taskId, "KNOWLEDGE_DRAFT_INCOMPLETE", "知识草稿尚不完整");
            return PublishStage.error(HttpStatus.CONFLICT, "KNOWLEDGE_DRAFT_INCOMPLETE", "知识草稿尚不完整",
                    V3Support.map("kind", "knowledge_draft_incomplete", "missingFields", missing));
        }
        List<String> changed = changedSources(draft);
        if (!changed.isEmpty()) {
            failTask(taskId, "SOURCE_CHANGED", "来源版本已经变化");
            return PublishStage.error(HttpStatus.CONFLICT, "SOURCE_CHANGED", "来源版本已经变化",
                    V3Support.map("kind", "source_changed", "sourceIds", changed));
        }
        if (!"KEYWORD_DEMO".equals(retrievalMode)) {
            failTask(taskId, "VECTOR_CONFIGURATION_UNAVAILABLE", "向量检索尚未配置");
            return PublishStage.error(HttpStatus.SERVICE_UNAVAILABLE, "VECTOR_CONFIGURATION_UNAVAILABLE",
                    "向量检索尚未配置", null);
        }
        int draftRevision = number(document.get("draft_revision"));
        Map<String, Object> snapshot = snapshot(documentId, draftRevision, draft);
        String snapshotHash = V3Support.sha256(snapshot);
        Map<String, Object> config = keywordConfig();
        String configHash = V3Support.sha256(config);
        int taskUpdate = jdbc.update("""
                UPDATE knowledge_publish_task SET status='PENDING',draft_revision=?,input_snapshot=?,
                  snapshot_hash=?,retrieval_mode='KEYWORD_DEMO',index_config=?,config_hash=?
                WHERE id=? AND status='RESERVED'
                """, draftRevision, json(snapshot), snapshotHash, json(config), configHash, taskId);
        int documentUpdate = jdbc.update("""
                UPDATE knowledge_document SET current_task_id=?,row_version=row_version+1
                WHERE id=? AND row_version=? AND current_task_id IS NULL
                """, taskId, documentId, expectedVersion);
        if (taskUpdate != 1 || documentUpdate != 1) {
            throw publishUnavailable();
        }
        return PublishStage.ok(expectedVersion + 1);
    }

    private void completeKeyword(String taskId) {
        try {
            Boolean claimed = transaction.execute(status -> {
                PublicationLock locked = lockPublication(taskId);
                if (locked == null || locked.document() == null) {
                    return false;
                }
                Map<String, Object> document = locked.document();
                Map<String, Object> task = locked.task();
                if (!taskId.equals(document.get("current_task_id")) || !"PENDING".equals(task.get("status"))) {
                    return false;
                }
                if (Instant.now().isAfter(timestamp(task.get("deadline_at")))) {
                    failCurrentPublication(locked, "BUILD_DEADLINE_EXCEEDED", "知识发布已超过截止时间");
                    return false;
                }
                return jdbc.update("UPDATE knowledge_publish_task SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",
                        taskId) == 1;
            });
            if (!Boolean.TRUE.equals(claimed)) {
                return;
            }
            transaction.executeWithoutResult(status -> {
                PublicationLock locked = lockPublication(taskId);
                if (locked == null || locked.document() == null) {
                    return;
                }
                Map<String, Object> document = locked.document();
                Map<String, Object> task = locked.task();
                if (!taskId.equals(document.get("current_task_id")) || !"RUNNING".equals(task.get("status"))) {
                    return;
                }
                if (Instant.now().isAfter(timestamp(task.get("deadline_at")))) {
                    failCurrentPublication(locked, "BUILD_DEADLINE_EXCEEDED", "知识发布已超过截止时间");
                    return;
                }
                Map<String, Object> receipt = V3Support.map("receiptType", "KEYWORD_READY", "taskId", taskId,
                        "documentId", task.get("document_id"), "snapshotHash", task.get("snapshot_hash"),
                        "configHash", task.get("config_hash"), "status", "READY", "buildId", taskId,
                        "checkedAt", V3Support.now());
                int taskUpdate = jdbc.update("""
                        UPDATE knowledge_publish_task SET status='SUCCEEDED',build_receipt=?,finished_at=CURRENT_TIMESTAMP
                        WHERE id=? AND status='RUNNING'
                        """, json(receipt), taskId);
                int documentUpdate = jdbc.update("""
                        UPDATE knowledge_document SET live_snapshot=?,live_snapshot_hash=?,active_task_id=?,
                          visibility='PUBLISHED',current_task_id=NULL,published_at=CURRENT_TIMESTAMP,
                          row_version=row_version+1
                        WHERE id=? AND current_task_id=? AND draft_revision=?
                        """, task.get("input_snapshot"), task.get("snapshot_hash"), taskId, document.get("id"), taskId,
                        task.get("draft_revision"));
                if (taskUpdate != 1 || documentUpdate != 1) {
                    throw publishUnavailable();
                }
            });
        } catch (Exception exception) {
            try {
                transaction.executeWithoutResult(status -> {
                    PublicationLock locked = lockPublication(taskId);
                    if (locked == null || locked.document() == null
                            || !taskId.equals(locked.document().get("current_task_id"))
                            || !Set.of("PENDING", "RUNNING").contains(locked.task().get("status"))) {
                        return;
                    }
                    failCurrentPublication(locked, "VECTOR_BUILD_UNAVAILABLE", "知识构建暂时不可用");
                });
            } catch (Exception ignored) {
                // 后台任务失败不输出敏感底层异常，任务期限扫描仍可收口。
            }
        }
    }

    private List<Map<String, Object>> eligibleRows() {
        return jdbc.queryForList("""
                SELECT d.*,t.retrieval_mode,t.config_hash,t.snapshot_hash AS task_snapshot_hash
                FROM knowledge_document d JOIN knowledge_publish_task t ON t.id=d.active_task_id
                WHERE d.visibility='PUBLISHED' AND d.live_snapshot IS NOT NULL
                  AND t.status='SUCCEEDED' AND t.snapshot_hash=d.live_snapshot_hash
                ORDER BY d.published_at DESC,d.id DESC
                """);
    }

    private Map<String, Object> publicView(Map<String, Object> row) {
        Map<String, Object> snapshot = readJson(row.get("live_snapshot"));
        return V3Support.map("id", row.get("id"), "title", snapshot.get("title"), "content", snapshot.get("content"),
                "tags", snapshot.get("tags"), "region", snapshot.get("region"), "periodText", snapshot.get("periodText"),
                "evidenceCategory", snapshot.get("evidenceCategory"), "usageLimitations", snapshot.get("usageLimitations"),
                "demoData", snapshot.get("demoData"), "references", references(row.get("id").toString(), snapshot),
                "publishedAt", V3Support.utc(row.get("published_at")));
    }

    private Map<String, Object> evidence(Map<String, Object> row) {
        Map<String, Object> snapshot = readJson(row.get("live_snapshot"));
        return V3Support.map("documentId", row.get("id"), "buildId", row.get("active_task_id"),
                "snapshotHash", row.get("live_snapshot_hash"), "title", snapshot.get("title"),
                "content", snapshot.get("content"), "tags", snapshot.get("tags"), "region", snapshot.get("region"),
                "periodText", snapshot.get("periodText"), "evidenceCategory", snapshot.get("evidenceCategory"),
                "usageLimitations", snapshot.get("usageLimitations"), "demoData", snapshot.get("demoData"),
                "references", references(row.get("id").toString(), snapshot));
    }

    private List<Map<String, Object>> references(String documentId, Map<String, Object> snapshot) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> sources = (List<Map<String, Object>>) snapshot.get("sources");
        return sources.stream().map(source -> (Map<String, Object>) V3Support.map(
                "sourceTitle", source.get("sourceTitle"),
                "detailPath", "/api/knowledge-documents/" + documentId)).toList();
    }

    private Map<String, Object> adminView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "candidateCode", row.get("candidate_code"),
                "version", row.get("row_version"), "draftRevision", row.get("draft_revision"),
                "visibility", row.get("visibility"), "draft", readJson(row.get("draft_json")),
                "liveSnapshot", row.get("live_snapshot") == null ? null : readJson(row.get("live_snapshot")),
                "activeTaskId", row.get("active_task_id"), "currentTaskId", row.get("current_task_id"),
                "editable", row.get("current_task_id") == null, "createdAt", V3Support.utc(row.get("created_at")),
                "updatedAt", V3Support.utc(row.get("updated_at")), "publishedAt", V3Support.utc(row.get("published_at")));
    }

    private Map<String, Object> taskView(Map<String, Object> row) {
        boolean accepted = row.get("input_snapshot") != null && Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "INVALIDATED")
                .contains(row.get("status"));
        Object error = "FAILED".equals(row.get("status")) ? V3Support.map("code", row.get("error_code"),
                "message", row.get("error_message")) : null;
        return V3Support.map("taskId", row.get("id"), "documentId", row.get("document_id"),
                "status", row.get("status"), "accepted", accepted, "retrievalMode", row.get("retrieval_mode"),
                "draftRevision", row.get("draft_revision"), "snapshotHash", row.get("snapshot_hash"),
                "configHash", row.get("config_hash"), "createdAt", V3Support.utc(row.get("created_at")),
                "startedAt", V3Support.utc(row.get("started_at")), "deadlineAt", V3Support.utc(row.get("deadline_at")),
                "finishedAt", V3Support.utc(row.get("finished_at")),
                "buildReceipt", row.get("build_receipt") == null ? null : readJson(row.get("build_receipt")),
                "error", error);
    }

    private SourceValue sourceValue(Map<String, Object> body, boolean update) {
        if (update) {
            V3Support.onlyKeys(body, "expectedVersion", "sourceKey", "title", "url", "publisher", "sourceKind",
                    "publicationDateText", "readAt", "locator", "readStatus");
            requirePresent(body, "expectedVersion");
        } else {
            V3Support.onlyKeys(body, "sourceKey", "title", "url", "publisher", "sourceKind",
                    "publicationDateText", "readAt", "locator", "readStatus");
        }
        requirePresent(body, "sourceKey", "title", "url", "publisher", "sourceKind", "publicationDateText",
                "readAt", "locator", "readStatus");
        String sourceKey = V3Support.rawRequiredText(body, "sourceKey");
        if (!sourceKey.matches("^[A-Za-z0-9_-]{1,40}$")) {
            V3Support.bad("sourceKey 格式无效");
        }
        String url = nullableText(body, "url", 2048);
        if (url != null) {
            try {
                URI parsed = new URI(url);
                if (!parsed.isAbsolute() || !("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme()))) {
                    V3Support.bad("url 必须是绝对 HTTP(S) 地址");
                }
            } catch (URISyntaxException exception) {
                V3Support.bad("url 格式无效");
            }
        }
        String readStatus = V3Support.rawRequiredText(body, "readStatus");
        if (!READ_STATUSES.contains(readStatus)) {
            V3Support.bad("readStatus 无效");
        }
        Timestamp readAt = body.get("readAt") == null ? null : Timestamp.from(parseInstant(body.get("readAt"), "readAt"));
        if (REVIEWED_STATUSES.contains(readStatus) != (readAt != null)) {
            V3Support.bad("readAt 与 readStatus 不匹配");
        }
        return new SourceValue(sourceKey, V3Support.requiredText(body, "title", 1, 200), url,
                nullableText(body, "publisher", 200), V3Support.requiredText(body, "sourceKind", 1, 80),
                nullableText(body, "publicationDateText", 80), readAt, nullableText(body, "locator", 300), readStatus);
    }

    private Map<String, Object> resolvedDraft(Map<String, Object> draft) {
        V3Support.onlyKeys(draft, "title", "content", "tags", "region", "periodText", "evidenceCategory",
                "usageLimitations", "demoData", "sourceBindings");
        requirePresent(draft, "title", "content", "tags", "region", "periodText", "evidenceCategory",
                "usageLimitations", "demoData", "sourceBindings");
        if (!(draft.get("demoData") instanceof Boolean)) {
            V3Support.bad("demoData 必须是布尔值");
        }
        List<Map<String, Object>> bindings = V3Support.objectList(draft, "sourceBindings", 0, 20);
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Map<String, Object> binding : bindings.stream()
                .sorted(Comparator.comparing(value -> value.get("sourceId").toString())).toList()) {
            V3Support.onlyKeys(binding, "sourceId", "expectedSourceVersion");
            String sourceId = V3Support.uuid(binding, "sourceId");
            if (!seen.add(sourceId)) {
                V3Support.bad("sourceBindings 不能重复");
            }
            int expected = V3Support.positiveInt(binding, "expectedSourceVersion");
            Map<String, Object> source = jdbc.queryForList("SELECT * FROM knowledge_source WHERE id=?", sourceId)
                    .stream().findFirst().orElse(null);
            if (source == null || number(source.get("source_version")) != expected) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "SOURCE_CHANGED", "来源版本已经变化",
                        V3Support.map("kind", "source_changed", "sourceIds", List.of(sourceId)));
            }
            snapshots.add(sourceSnapshot(source));
        }
        return V3Support.map("title", nullableText(draft, "title", 120),
                "content", nullableText(draft, "content", 30000),
                "tags", strictStrings(draft, "tags", 20, 80), "region", nullableText(draft, "region", 200),
                "periodText", nullableText(draft, "periodText", 200),
                "evidenceCategory", nullableText(draft, "evidenceCategory", 200),
                "usageLimitations", strictStrings(draft, "usageLimitations", 10, 300),
                "demoData", draft.get("demoData"), "sourceSnapshots", snapshots);
    }

    private Map<String, Object> sourceSnapshot(Map<String, Object> row) {
        return V3Support.map("sourceId", row.get("id"), "sourceVersion", row.get("source_version"),
                "sourceKey", row.get("source_key"), "sourceTitle", row.get("title"),
                "publisher", row.get("publisher"), "sourceKind", row.get("source_kind"),
                "publicationDateText", row.get("publication_date_text"), "readAt", V3Support.utc(row.get("read_at")),
                "locator", row.get("locator"), "readStatus", row.get("read_status"));
    }

    private Map<String, Object> sourceView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "version", row.get("source_version"),
                "sourceKey", row.get("source_key"), "title", row.get("title"), "url", row.get("url"),
                "publisher", row.get("publisher"), "sourceKind", row.get("source_kind"),
                "publicationDateText", row.get("publication_date_text"), "readAt", V3Support.utc(row.get("read_at")),
                "locator", row.get("locator"), "readStatus", row.get("read_status"),
                "createdAt", V3Support.utc(row.get("created_at")), "updatedAt", V3Support.utc(row.get("updated_at")));
    }

    private Map<String, Object> snapshot(String documentId, int draftRevision, Map<String, Object> draft) {
        return V3Support.map("documentId", documentId, "draftRevision", draftRevision,
                "title", draft.get("title"), "content", draft.get("content"), "tags", draft.get("tags"),
                "region", draft.get("region"), "periodText", draft.get("periodText"),
                "evidenceCategory", draft.get("evidenceCategory"), "usageLimitations", draft.get("usageLimitations"),
                "demoData", draft.get("demoData"), "sources", draft.get("sourceSnapshots"));
    }

    private List<String> publicationMissing(Map<String, Object> draft) {
        List<String> result = new ArrayList<>();
        if (draft.get("title") == null) result.add("title");
        if (draft.get("content") == null) result.add("content");
        if (draft.get("evidenceCategory") == null) result.add("evidenceCategory");
        List<Map<String, Object>> sources = castMaps(draft.get("sourceSnapshots"));
        if (sources.isEmpty()) result.add("sources");
        if (sources.stream().anyMatch(source -> !REVIEWED_STATUSES.contains(source.get("readStatus")))) {
            result.add("sourceReadStatus");
        }
        return result;
    }

    private List<String> changedSources(Map<String, Object> draft) {
        List<String> changed = new ArrayList<>();
        for (Map<String, Object> snapshot : castMaps(draft.get("sourceSnapshots"))) {
            String id = snapshot.get("sourceId").toString();
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT source_version FROM knowledge_source WHERE id=? FOR UPDATE", id);
            if (rows.isEmpty() || number(rows.getFirst().get("source_version")) != number(snapshot.get("sourceVersion"))) {
                changed.add(id);
            }
        }
        changed.sort(String::compareTo);
        return changed;
    }

    private void failTask(String taskId, String code, String message) {
        jdbc.update("""
                UPDATE knowledge_publish_task SET status='FAILED',error_code=?,error_message=?,finished_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='RESERVED'
                """, code, message, taskId);
    }

    private PublicationLock lockPublication(String taskId) {
        Map<String, Object> reference = jdbc.queryForList(
                        "SELECT document_id FROM knowledge_publish_task WHERE id=?", taskId)
                .stream().findFirst().orElse(null);
        if (reference == null) {
            return null;
        }
        String documentId = reference.get("document_id").toString();
        Map<String, Object> document = jdbc.queryForList(
                        "SELECT * FROM knowledge_document WHERE id=? FOR UPDATE", documentId)
                .stream().findFirst().orElse(null);
        Map<String, Object> task = jdbc.queryForList(
                        "SELECT * FROM knowledge_publish_task WHERE id=? FOR UPDATE", taskId)
                .stream().findFirst().orElse(null);
        if (task == null || !documentId.equals(task.get("document_id").toString())) {
            return null;
        }
        return new PublicationLock(document, task);
    }

    private void failCurrentPublication(PublicationLock locked, String code, String message) {
        Map<String, Object> document = locked.document();
        Map<String, Object> task = locked.task();
        String taskId = task.get("id").toString();
        String originalStatus = task.get("status").toString();
        int taskUpdate = jdbc.update("""
                UPDATE knowledge_publish_task SET status='FAILED',error_code=?,error_message=?,finished_at=CURRENT_TIMESTAMP
                WHERE id=? AND status=?
                """, code, message, taskId, originalStatus);
        int documentUpdate = jdbc.update("""
                UPDATE knowledge_document SET current_task_id=NULL,row_version=row_version+1
                WHERE id=? AND current_task_id=?
                """, document.get("id"), taskId);
        if (taskUpdate != 1 || documentUpdate != 1) {
            throw publishUnavailable();
        }
    }

    private Map<String, Object> lockedDocument(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM knowledge_document WHERE id=? FOR UPDATE", id)
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return row;
    }

    private Map<String, Object> documentRow(String id, boolean publicOnly) {
        return jdbc.queryForList("SELECT * FROM knowledge_document WHERE id=?"
                + (publicOnly ? " AND visibility='PUBLISHED'" : ""), id).stream().findFirst().orElse(null);
    }

    private Map<String, Object> keywordConfig() {
        return V3Support.map("retrievalMode", "KEYWORD_DEMO", "schemaVersion", "knowledge-keyword-v1",
                "matchingAlgorithm", "JAVA_LITERAL_TOKEN_V1", "maxResults", Math.min(maxResults, 10));
    }

    public String keywordConfigHash() {
        return V3Support.sha256(keywordConfig());
    }

    private ValidatedSummary validateSummary(Map<String, Object> body) {
        V3Support.onlyKeys(body, "threadId", "runId", "summarySequence", "runState", "agentType",
                "retrievalMode", "startedAt", "finishedAt", "knowledgeDependencies", "candidateRefs", "errorCode");
        requirePresent(body, "threadId", "runId", "summarySequence", "runState", "agentType", "retrievalMode",
                "startedAt", "finishedAt", "knowledgeDependencies", "candidateRefs", "errorCode");
        String threadId = V3Support.uuid(body, "threadId");
        String runId = V3Support.uuid(body, "runId");
        int sequence = V3Support.positiveInt(body, "summarySequence");
        String state = V3Support.rawRequiredText(body, "runState");
        String agentType = V3Support.rawRequiredText(body, "agentType");
        String mode = V3Support.rawRequiredText(body, "retrievalMode");
        if (!RUN_STATES.contains(state) || !Set.of("KNOWLEDGE_GUIDE", "SERVICE_RECOMMENDER", "ITINERARY_PLANNER").contains(agentType)
                || !Set.of("NONE", "KEYWORD_DEMO", "VECTOR").contains(mode)) {
            V3Support.bad("运行摘要枚举值无效");
        }
        Instant startedAt = parseInstant(body.get("startedAt"), "startedAt");
        Instant finishedAt = body.get("finishedAt") == null ? null : parseInstant(body.get("finishedAt"), "finishedAt");
        String errorCode = body.get("errorCode") == null ? null : body.get("errorCode").toString();
        if (errorCode != null && !errorCode.matches("^[A-Z0-9_]{1,64}$")) {
            V3Support.bad("errorCode 格式无效");
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            V3Support.bad("finishedAt 不能早于 startedAt");
        }
        if ((Set.of("RUNNING", "CANCEL_REQUESTED").contains(state) && (finishedAt != null || errorCode != null))
                || ("COMPLETED".equals(state) && (finishedAt == null || errorCode != null))
                || ("STOPPED".equals(state) && (finishedAt == null || !"CANCELLED_BY_USER".equals(errorCode)))
                || (Set.of("INTERRUPTED", "FAILED").contains(state) && (finishedAt == null || errorCode == null))) {
            V3Support.bad("运行状态与 finishedAt/errorCode 不匹配");
        }
        List<Map<String, Object>> dependencies = V3Support.objectList(body, "knowledgeDependencies", 0, 50);
        Set<String> dependencyKeys = new HashSet<>();
        for (Map<String, Object> dependency : dependencies) {
            V3Support.onlyKeys(dependency, "documentId", "buildId", "snapshotHash");
            String key = V3Support.uuid(dependency, "documentId") + ":" + V3Support.uuid(dependency, "buildId");
            V3Support.digest(dependency, "snapshotHash");
            if (!dependencyKeys.add(key)) V3Support.bad("knowledgeDependencies 不能重复");
        }
        if ("NONE".equals(mode) && !dependencies.isEmpty()) {
            V3Support.bad("retrievalMode=NONE 时知识依赖必须为空");
        }
        List<Map<String, Object>> refs = V3Support.objectList(body, "candidateRefs", 0, 20);
        Set<String> refKeys = new HashSet<>();
        for (Map<String, Object> ref : refs) {
            V3CandidateClient.validateCandidateRef(ref);
            if (!threadId.equals(ref.get("threadId")) || !refKeys.add(V3Support.canonicalJson(ref))) {
                V3Support.bad("candidateRefs 的 threadId 必须匹配且不能重复");
            }
        }
        return new ValidatedSummary(threadId, runId, sequence, state, agentType, mode, startedAt,
                finishedAt, dependencies, refs, errorCode);
    }

    private Map<String, Object> runSummaryResult(ValidatedSummary summary, int storedSequence,
                                                  String storedState, String outcome, String receivedAt) {
        return V3Support.map("threadId", summary.threadId(), "runId", summary.runId(),
                "receivedSequence", summary.sequence(), "storedSequence", storedSequence,
                "storedState", storedState, "outcome", outcome, "receivedAt", receivedAt);
    }

    private static boolean validRunTransition(String current, String next) {
        return current.equals(next) || "RUNNING".equals(current) && ("CANCEL_REQUESTED".equals(next)
                || V3Support.terminalRunState(next)) || "CANCEL_REQUESTED".equals(current)
                && V3Support.terminalRunState(next);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static List<String> keywordTokens(String value) {
        String normalized = normalize(value).replaceAll("\\s+", "");
        List<String> tokens = new ArrayList<>();
        if (!normalized.isEmpty()) {
            tokens.add(normalized);
        }
        for (int index = 0; index + 1 < normalized.length(); index++) {
            String pair = normalized.substring(index, index + 2);
            if (pair.codePoints().anyMatch(Character::isIdeographic)) {
                tokens.add(pair);
            }
        }
        return tokens.stream().distinct().toList();
    }

    private static Instant parseInstant(Object value, String field) {
        if (!(value instanceof String text)) {
            V3Support.bad("字段 " + field + " 必须是 UTC 时间字符串");
        }
        try {
            Instant instant = Instant.parse((String) value);
            if (instant.getNano() != 0) V3Support.bad("字段 " + field + " 必须是秒精度");
            return instant;
        } catch (Exception exception) {
            V3Support.bad("字段 " + field + " 时间格式无效");
            return null;
        }
    }

    private static Instant timestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        return Instant.parse(value.toString());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("知识 JSON 无法序列化", exception);
        }
    }

    private Map<String, Object> readJson(Object value) {
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("知识 JSON 无法读取", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static List<String> strictStrings(Map<String, Object> body, String key, int maxItems, int maxLength) {
        return V3Support.stringList(body, key, maxItems, maxLength);
    }

    private static String nullableText(Map<String, Object> body, String key, int maxLength) {
        Object value = body.get(key);
        if (value == null) return null;
        if (!(value instanceof String text)) V3Support.bad("字段 " + key + " 必须是字符串或 null");
        String normalized = ((String) value).trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maxLength) V3Support.bad("字段 " + key + " 长度不符合要求");
        return normalized;
    }

    private static String candidateCode(Object value) {
        if (value == null) return null;
        if (!(value instanceof String text) || !text.trim().matches("^[A-Za-z0-9_-]{1,40}$")) {
            V3Support.bad("candidateCode 格式无效");
        }
        return ((String) value).trim();
    }

    private static void requirePresent(Map<String, Object> body, String... keys) {
        for (String key : keys) if (!body.containsKey(key)) V3Support.bad("字段 " + key + " 必须出现");
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static ApiRequestException versionConflict(int expected, int current) {
        return new ApiRequestException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "资源版本已变化",
                V3Support.map("kind", "version_conflict", "expectedVersion", expected, "currentVersion", current));
    }

    private static ApiRequestException publishUnavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "PUBLISH_RESULT_UNAVAILABLE",
                "发布结果暂时无法确认");
    }

    private static ApiRequestException runUnavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "RUN_SUMMARY_UNAVAILABLE",
                "运行摘要暂时无法确认");
    }

    public record PublishResult(Map<String, Object> data, boolean replayed) {
    }

    private record SourceValue(String sourceKey, String title, String url, String publisher, String sourceKind,
                               String publicationDateText, Timestamp readAt, String locator, String readStatus) {
    }

    private record PublishStage(int documentVersion, HttpStatus errorStatus, String errorCode,
                                String errorMessage, Object details) {
        static PublishStage ok(int version) {
            return new PublishStage(version, null, null, null, null);
        }

        static PublishStage error(HttpStatus status, String code, String message, Object details) {
            return new PublishStage(0, status, code, message, details);
        }
    }

    private record PublicationLock(Map<String, Object> document, Map<String, Object> task) {
    }

    private record ScoredEvidence(Map<String, Object> row, Map<String, Object> evidence, int score, int titleHits) {
    }

    private record ValidatedSummary(String threadId, String runId, int sequence, String runState, String agentType,
                                    String retrievalMode, Instant startedAt, Instant finishedAt,
                                    List<Map<String, Object>> dependencies,
                                    List<Map<String, Object>> candidateRefs, String errorCode) {
    }
}
