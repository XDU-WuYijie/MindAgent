-- MindAgent chat memory refactor:
-- - introduce chat_session / chat_session_memory / chat_memory_compress_log
-- - replace chat_messages with session-based chat_message

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

DROP TABLE IF EXISTS chat_messages;
