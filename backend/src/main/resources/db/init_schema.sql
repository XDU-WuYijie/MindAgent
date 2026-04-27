CREATE DATABASE IF NOT EXISTS mindagent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE mindagent;

CREATE TABLE IF NOT EXISTS app_users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(200) NOT NULL,
  role VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  last_message_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_session_user_id (user_id),
  KEY idx_chat_session_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  content LONGTEXT NOT NULL,
  token_count INT NOT NULL,
  message_status VARCHAR(32) NOT NULL,
  compressed TINYINT(1) NOT NULL DEFAULT 0,
  metadata LONGTEXT DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_message_session_id (session_id),
  KEY idx_chat_message_user_id (user_id),
  KEY idx_chat_message_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_session_memory (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  summary_text LONGTEXT NOT NULL,
  summary_token_count INT NOT NULL,
  summarized_until_message_id BIGINT DEFAULT NULL,
  version INT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_session_memory_session_id (session_id),
  KEY idx_chat_session_memory_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_memory_compress_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  from_message_id BIGINT DEFAULT NULL,
  to_message_id BIGINT DEFAULT NULL,
  source_message_count INT NOT NULL,
  source_token_count INT NOT NULL,
  summary_version INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_memory_compress_log_session_id (session_id),
  KEY idx_chat_memory_compress_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mcp_dispatch_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_mcp_dispatch_logs_user_id (user_id),
  KEY idx_mcp_dispatch_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS psychological_reports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  query_text LONGTEXT NOT NULL,
  intent VARCHAR(32) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  rag_contexts INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_psychological_reports_user_id (user_id),
  KEY idx_psychological_reports_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_tokens_user_id (user_id),
  KEY idx_refresh_tokens_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kb_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  knowledge_base_key VARCHAR(32) NOT NULL,
  doc_name VARCHAR(255) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  audience VARCHAR(255) DEFAULT NULL,
  version VARCHAR(64) DEFAULT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(500) DEFAULT NULL,
  file_bytes BIGINT NOT NULL,
  chunk_count INT NOT NULL DEFAULT 0,
  vector_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  processed_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_document_space_stored_filename (knowledge_base_key, stored_filename),
  KEY idx_kb_document_status_created_at (status, created_at),
  KEY idx_kb_document_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kb_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  chunk_id VARCHAR(255) NOT NULL,
  section_key VARCHAR(64) NOT NULL,
  section_title VARCHAR(255) NOT NULL,
  category VARCHAR(255) DEFAULT NULL,
  tags VARCHAR(500) DEFAULT NULL,
  risk_level VARCHAR(16) DEFAULT NULL,
  source_page_range VARCHAR(64) DEFAULT NULL,
  question_text LONGTEXT DEFAULT NULL,
  answer_text LONGTEXT DEFAULT NULL,
  content LONGTEXT NOT NULL,
  metadata_json LONGTEXT NOT NULL,
  token_count INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_chunk_chunk_id (chunk_id),
  KEY idx_kb_chunk_document_id (document_id),
  KEY idx_kb_chunk_document_order (document_id, chunk_index),
  CONSTRAINT fk_kb_chunk_document
    FOREIGN KEY (document_id) REFERENCES kb_document (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_users (username, password_hash, role)
SELECT 'admin', '$2y$10$6Ir/CSrUgitVwqC2wpEEiuSBw9CaObgci8P3CT33PSQrbj5byAGZi', 'ADMIN'
WHERE NOT EXISTS (
  SELECT 1 FROM app_users WHERE username = 'admin'
);

INSERT INTO app_users (username, password_hash, role)
SELECT 'user', '$2y$10$82UWSRiS9bxt.CLeYGk0W.xmMrXIgcTWmbZ3Y4JCtuG6WlPzApKDG', 'USER'
WHERE NOT EXISTS (
  SELECT 1 FROM app_users WHERE username = 'user'
);
