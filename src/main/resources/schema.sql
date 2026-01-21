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
