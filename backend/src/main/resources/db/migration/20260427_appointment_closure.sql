CREATE TABLE teacher_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  title VARCHAR(64) DEFAULT NULL,
  office_location VARCHAR(128) DEFAULT NULL,
  contact_phone VARCHAR(32) DEFAULT NULL,
  bio LONGTEXT DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_profile_user_id (user_id)
);

CREATE TABLE teacher_available_slot (
  id BIGINT NOT NULL AUTO_INCREMENT,
  teacher_user_id BIGINT NOT NULL,
  start_time DATETIME(6) NOT NULL,
  end_time DATETIME(6) NOT NULL,
  status VARCHAR(16) NOT NULL,
  location VARCHAR(128) DEFAULT NULL,
  notes VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_teacher_available_slot_teacher_start (teacher_user_id, start_time),
  KEY idx_teacher_available_slot_status_start (status, start_time)
);

CREATE TABLE counseling_appointment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  slot_id BIGINT NOT NULL,
  teacher_user_id BIGINT NOT NULL,
  student_user_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  student_note VARCHAR(255) DEFAULT NULL,
  cancel_reason VARCHAR(255) DEFAULT NULL,
  completed_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_counseling_appointment_student_created (student_user_id, created_at),
  KEY idx_counseling_appointment_teacher_created (teacher_user_id, created_at),
  KEY idx_counseling_appointment_slot_status (slot_id, status)
);

CREATE TABLE appointment_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_appointment_log_appointment_created (appointment_id, created_at)
);

CREATE TABLE ai_tool_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  tool_name VARCHAR(64) NOT NULL,
  tool_arguments LONGTEXT DEFAULT NULL,
  tool_result LONGTEXT DEFAULT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_tool_call_log_user_created (user_id, created_at),
  KEY idx_ai_tool_call_log_session_created (session_id, created_at)
);
