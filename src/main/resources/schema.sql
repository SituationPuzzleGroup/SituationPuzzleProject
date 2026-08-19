-- MySQL / MariaDB schema（計劃書 v0.2）

CREATE TABLE IF NOT EXISTS story (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  story_order INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  result_text TEXT NOT NULL,
  ask_prompt_text TEXT NULL,
  low_score_summary TEXT NOT NULL,
  high_score_summary TEXT NOT NULL,
  finish_text TEXT NULL,
  truth_score_threshold INT NOT NULL DEFAULT 60,
  truth_card TEXT NULL,
  real_case_text TEXT NULL,
  real_case_url VARCHAR(500) NULL,
  real_case_label VARCHAR(200) NULL,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uk_story_order (story_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 既有庫升級（欄位已存在則略過）
SET @db := DATABASE();
SET @sql := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'story' AND COLUMN_NAME = 'real_case_url') = 0,
    'ALTER TABLE story ADD COLUMN real_case_url VARCHAR(500) NULL, ADD COLUMN real_case_label VARCHAR(200) NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS story_option (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  story_id BIGINT NOT NULL,
  option_text TEXT NOT NULL,
  reply_text TEXT NOT NULL,
  is_correct TINYINT(1) NOT NULL DEFAULT 0,
  score_value INT NOT NULL DEFAULT 20,
  hint_tag VARCHAR(100) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  KEY idx_story_option_story_sort (story_id, sort_order),
  CONSTRAINT fk_story_option_story FOREIGN KEY (story_id) REFERENCES story (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dialogue_script (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  script_key VARCHAR(100) NOT NULL,
  title VARCHAR(200) NULL,
  content TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uk_dialogue_script_key (script_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_key VARCHAR(100) NOT NULL,
  content TEXT NOT NULL,
  description VARCHAR(500) NULL,
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uk_ai_prompt_key (template_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS game_config (
  config_key VARCHAR(100) NOT NULL PRIMARY KEY,
  config_value VARCHAR(500) NOT NULL,
  description VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
