-- 修复上传文档时字段长度不足导致的截断错误
-- 扩大 kb_chunk.source_page_range 和 kb_document.error_message 字段长度

ALTER TABLE kb_chunk
    MODIFY COLUMN source_page_range VARCHAR(500) DEFAULT NULL;

ALTER TABLE kb_document
    MODIFY COLUMN error_message VARCHAR(2000) DEFAULT NULL;
