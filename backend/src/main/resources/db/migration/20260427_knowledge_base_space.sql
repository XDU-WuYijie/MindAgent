ALTER TABLE kb_document
  ADD COLUMN knowledge_base_key VARCHAR(32) NOT NULL DEFAULT 'knowledge_base_1' AFTER storage_path;

UPDATE kb_document
SET knowledge_base_key = 'knowledge_base_1'
WHERE knowledge_base_key IS NULL OR knowledge_base_key = '';

ALTER TABLE kb_document
  DROP INDEX uk_kb_document_stored_filename,
  ADD UNIQUE KEY uk_kb_document_space_stored_filename (knowledge_base_key, stored_filename);
