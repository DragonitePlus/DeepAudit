CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    trace_id VARCHAR(36) PRIMARY KEY,
    app_user_id VARCHAR(64),
    sql_template TEXT,
    table_names VARCHAR(255),
    risk_score INT,
    result_count INT,
    action_taken VARCHAR(20),
    create_time DATETIME,
    
    -- Extension fields for future proofing and detailed audit
    client_ip VARCHAR(50) COMMENT 'Client IP Address',
    execution_time BIGINT COMMENT 'SQL Execution Time (ms)',
    extra_info TEXT COMMENT 'JSON/Text for dynamic extension data (e.g. headers, stack trace)'
);

CREATE TABLE IF NOT EXISTS sys_risk_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100),
    regex VARCHAR(255),
    score INT
);

CREATE TABLE IF NOT EXISTS sys_sensitive_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL UNIQUE,
    sensitivity_level INT DEFAULT 1 COMMENT '1:Low, 2:Medium, 3:High, 4:Critical',
    coefficient DOUBLE DEFAULT 1.0 COMMENT 'Risk Multiplier'
);

CREATE TABLE IF NOT EXISTS sys_user_risk_profile (
    app_user_id VARCHAR(64) PRIMARY KEY,
    current_score INT DEFAULT 0,
    risk_level VARCHAR(20) DEFAULT 'NORMAL', -- NORMAL, OBSERVATION, BLOCKED
    last_update_time DATETIME,
    description VARCHAR(255)
);

-- Init Data
INSERT INTO sys_sensitive_table (table_name, sensitivity_level, coefficient) VALUES ('sys_user', 3, 1.5) ON DUPLICATE KEY UPDATE coefficient=1.5;
INSERT INTO sys_sensitive_table (table_name, sensitivity_level, coefficient) VALUES ('sys_risk_rule', 2, 1.2) ON DUPLICATE KEY UPDATE coefficient=1.2;
