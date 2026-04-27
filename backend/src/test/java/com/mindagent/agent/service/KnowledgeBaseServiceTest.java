package com.mindagent.agent.service;

import com.mindagent.agent.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceTest {

    @Test
    void shouldChunkFaqDocumentWithStructuredMetadata() throws Exception {
        KnowledgeBaseService service = newService();
        String faq = """
                ---
                title: 测试 FAQ
                source_type: faq
                audience: 大学生
                version: v1
                ---

                # 测试 FAQ

                ## FAQ-001 如何测试递归切块？

                **category:** 测试分类
                **tags:** 标签A, 标签B
                **source_type:** faq
                **risk_level:** low

                ### 问题
                如何测试递归切块？

                ### 回答
                %s
                """.formatted("这是一段很长的回答内容。".repeat(160));

        List<Document> documents = invokeChunk(service, "shu_faq.md", faq);

        assertFalse(documents.isEmpty());
        assertTrue(documents.size() >= 2, "long FAQ content should be split into multiple chunks");

        Map<String, Object> metadata = documents.get(0).getMetadata();
        assertTrue("测试 FAQ".equals(metadata.get("doc_name")));
        assertTrue("faq".equals(metadata.get("source_type")));
        assertTrue(metadata.containsKey("chunk_id"));
        assertTrue("测试分类".equals(metadata.get("category")));
        assertTrue(metadata.containsKey("tags"));
        assertTrue(metadata.containsKey("risk_level"));
        assertTrue(metadata.containsKey("audience"));
        assertTrue(metadata.containsKey("version"));
        assertTrue(metadata.containsKey("question_text"));
        assertTrue(metadata.containsKey("answer_text"));
        assertTrue(((String) documents.get(0).getText()).contains("### 问题"));
        assertTrue(((String) documents.get(0).getText()).contains("### 回答"));
    }

    @Test
    void shouldChunkKbDocumentWithPageRangeMetadata() throws Exception {
        KnowledgeBaseService service = newService();
        String kb = """
                ---
                title: 测试 KB
                source_type: article
                audience: 大学生
                version: v1
                ---

                # 测试 KB

                ## KB-001 长文档切块

                **category:** 测试分类
                **tags:** 标签A, 标签B
                **source_page_range:** 3-5
                **risk_level:** medium

                %s
                """.formatted("这是一段很长的知识库正文。".repeat(180));

        List<Document> documents = invokeChunk(service, "shu_clean.md", kb);

        assertFalse(documents.isEmpty());
        assertTrue(documents.size() >= 2, "long KB content should be split into multiple chunks");

        Map<String, Object> metadata = documents.get(0).getMetadata();
        assertTrue("测试 KB".equals(metadata.get("doc_name")));
        assertTrue("kb".equals(metadata.get("source_type")));
        assertTrue(metadata.containsKey("chunk_id"));
        assertTrue("测试分类".equals(metadata.get("category")));
        assertTrue(metadata.containsKey("tags"));
        assertTrue(metadata.containsKey("risk_level"));
        assertTrue(metadata.containsKey("audience"));
        assertTrue(metadata.containsKey("version"));
        assertTrue("3-5".equals(metadata.get("source_page_range")));
        assertTrue(!metadata.containsKey("question_text"));
        assertTrue(!metadata.containsKey("answer_text"));
        assertTrue(((String) documents.get(0).getText()).contains("## KB-001"));
    }

    @Test
    void shouldParseRealKnowledgeFiles() throws Exception {
        KnowledgeBaseService service = newService();
        Path faqPath = Path.of("storage", "knowledge", "knowlegde_base_1", "faq", "shu_faq.md").toAbsolutePath().normalize();
        Path kbPath = Path.of("storage", "knowledge", "knowlegde_base_1", "kb", "shu_clean.md").toAbsolutePath().normalize();

        List<Document> faqDocs = invokeChunk(service, "shu_faq.md", Files.readString(faqPath, StandardCharsets.UTF_8));
        List<Document> kbDocs = invokeChunk(service, "shu_clean.md", Files.readString(kbPath, StandardCharsets.UTF_8));

        assertFalse(faqDocs.isEmpty());
        assertFalse(kbDocs.isEmpty());
        assertTrue(faqDocs.stream().allMatch(doc -> "faq".equals(doc.getMetadata().get("source_type"))));
        assertTrue(kbDocs.stream().allMatch(doc -> "kb".equals(doc.getMetadata().get("source_type"))));
    }

    private KnowledgeBaseService newService() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setProvider("local");
        properties.setMinChunkLength(10);

        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        return new KnowledgeBaseService(properties, provider);
    }

    private List<Document> invokeChunk(KnowledgeBaseService service, String source, String raw) throws Exception {
        Method method = KnowledgeBaseService.class.getDeclaredMethod("chunkKnowledgeFile", String.class, String.class, List.class);
        method.setAccessible(true);
        List<Document> documents = new ArrayList<>();
        method.invoke(service, source, raw, documents);
        return documents;
    }
}
