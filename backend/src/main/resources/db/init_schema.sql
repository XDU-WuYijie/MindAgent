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

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  content LONGTEXT NOT NULL,
  intent VARCHAR(32) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_messages_user_id (user_id),
  KEY idx_chat_messages_created_at (created_at)
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
