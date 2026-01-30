-- ==========================================
-- 1. 智能审计日志表 (核心 AI 训练数据源)
-- ==========================================
CREATE TABLE IF NOT EXISTS sys_audit_log (
    -- 基础追踪
                                             trace_id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '全链路追踪ID (UUID)',
                                             app_user_id VARCHAR(64) NOT NULL COMMENT '业务操作用户ID',
                                             logic_db_name VARCHAR(64) DEFAULT NULL COMMENT '逻辑数据库名 (上下文隔离)',

    -- SQL 内容与指纹
                                             sql_template TEXT COMMENT '归一化后的SQL模板 (脱敏)',
                                             sql_hash CHAR(32) DEFAULT NULL COMMENT 'SQL模板MD5指纹 (AI特征: 频率统计)',
                                             table_names VARCHAR(512) DEFAULT NULL COMMENT '涉及表名 (逗号分隔)',

    -- 执行指标 (AI 特征)
                                             action_taken VARCHAR(20) NOT NULL COMMENT '动作: PASS/BLOCK',
                                             risk_score INT DEFAULT 0 COMMENT '综合风险分',
                                             result_count BIGINT DEFAULT 0 COMMENT 'DQL返回行数 (防拖库)',
                                             affected_rows BIGINT DEFAULT 0 COMMENT 'DML影响行数 (防删改)',
                                             execution_time BIGINT DEFAULT 0 COMMENT '耗时(ms) (防慢查询)',
                                             error_code INT DEFAULT 0 COMMENT '数据库错误码 (防探测)',

    -- 环境特征
                                             client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
                                             client_app VARCHAR(128) DEFAULT NULL COMMENT '客户端工具 (如 Navicat/Python)',

    -- 闭环反馈
                                             feedback_status TINYINT DEFAULT 0 COMMENT '人工反馈: 0-未标记, 1-误报(正常), 2-实锤(异常)',

    -- 扩展与时间
                                             extra_info TEXT COMMENT '扩展信息(JSON)',
                                             create_time DATETIME(3) NOT NULL COMMENT '记录时间(毫秒级)'
);

-- 索引优化 (对 AI 训练和查询至关重要)
CREATE INDEX idx_audit_create_time ON sys_audit_log(create_time);
CREATE INDEX idx_audit_user_time ON sys_audit_log(app_user_id, create_time);
CREATE INDEX idx_audit_hash_time ON sys_audit_log(sql_hash, create_time);
CREATE INDEX idx_audit_feedback ON sys_audit_log(feedback_status);

-- ==========================================
-- 2. 用户风险画像表 (存储实时状态)
-- ==========================================
CREATE TABLE `sys_user_risk_profile` (
                                         `app_user_id` varchar(64) NOT NULL COMMENT '业务用户ID (关联 sys_audit_log.app_user_id)',
                                         `current_score` int DEFAULT '0' COMMENT '当前累积风险分 (随时间衰减)',
                                         `risk_level` varchar(20) DEFAULT 'NORMAL' COMMENT '当前风险等级: NORMAL(正常), OBSERVATION(观察期), BLOCKED(已阻断)',
                                         `last_update_time` datetime DEFAULT NULL COMMENT '最后一次分值变动时间 (用于计算衰减)',
                                         `description` varchar(255) DEFAULT NULL COMMENT '状态描述 (如: 触发规则X导致进入观察期)',
                                         PRIMARY KEY (`app_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户实时风险画像表';

-- ==========================================
-- 3. 敏感表配置表 (静态规则)
-- ==========================================
CREATE TABLE `sys_sensitive_table` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                       `table_name` varchar(100) NOT NULL COMMENT '敏感表名 (支持通配符或精确匹配)',
                                       `sensitivity_level` int DEFAULT '1' COMMENT '敏感级别: 1-低(内部), 2-中(秘密), 3-高(机密), 4-极高(绝密)',
                                       `coefficient` double DEFAULT '1' COMMENT '风险系数 (Risk Multiplier): 基础风险分 * 系数 = 最终风险分',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `table_name` (`table_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据资产表';

-- ==========================================
-- 4. 风险规则配置表 (正则规则)
-- ==========================================
CREATE TABLE IF NOT EXISTS sys_risk_rule (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
                                             regex VARCHAR(255) NOT NULL COMMENT '正则表达式',
                                             score INT DEFAULT 10 COMMENT '命中扣分',
                                             is_enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用'
);



