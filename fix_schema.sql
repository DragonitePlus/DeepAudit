-- Fix for SysAuditLog schema mismatch
-- Run this in your MySQL database 'deepaudit_sys'

-- 1. Add missing Feedback and AST Feature columns
ALTER TABLE sys_audit_log 
    ADD COLUMN feedback_status TINYINT DEFAULT 0 COMMENT '人工反馈: 0-未标记, 1-误报(正常), 2-实锤(异常)',
    ADD COLUMN sql_hash CHAR(32) DEFAULT NULL COMMENT 'SQL模板MD5指纹',
    ADD COLUMN affected_rows BIGINT DEFAULT 0 COMMENT 'DML影响行数',
    ADD COLUMN error_code INT DEFAULT 0 COMMENT '数据库错误码',
    ADD COLUMN client_app VARCHAR(128) DEFAULT NULL COMMENT '客户端工具',
    ADD COLUMN condition_count INT DEFAULT 0 COMMENT 'AST: 条件数',
    ADD COLUMN join_count INT DEFAULT 0 COMMENT 'AST: Join数',
    ADD COLUMN nested_level INT DEFAULT 0 COMMENT 'AST: 嵌套层级',
    ADD COLUMN has_always_true TINYINT(1) DEFAULT 0 COMMENT 'AST: 是否包含恒真条件';

-- 2. Modify existing columns to avoid overflow (if they were INT)
-- Note: schema.sql defined them as BIGINT, but checking just in case
ALTER TABLE sys_audit_log MODIFY COLUMN result_count BIGINT DEFAULT 0;
ALTER TABLE sys_audit_log MODIFY COLUMN affected_rows BIGINT DEFAULT 0;
