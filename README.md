基于语义解析与行为基线的数据库异常访问检测系统

系统设计规格说明书 (System Design Specification)

文档版本：V1.0

适用对象：系统开发人员、指导教师

最后更新：2026年1月20日

1. 引言 (Introduction)

1.1 研究背景与痛点

随着微服务架构的演进，数据库访问面临两大核心挑战：

身份断层 (Identity Gap)：传统审计仅能记录数据库账号（DB User），无法关联应用层实际操作人员（App User），导致审计盲区。

防御僵化 (Rigid Blocking)：传统防火墙“非黑即白”的阻断策略缺乏弹性，易误伤正常业务，且难以识别“合法违规”的内鬼操作。

1.2 系统建设目标

本系统旨在构建一套集全链路审计、弹性风险控制、智能化异常检测于一体的数据安全防护体系。

精准溯源：利用 AOP 与 SQL 语义分析，还原“谁 (User)、何时 (Time)、查了什么 (Table/Field)”。

弹性防御：引入 RAdAC (风险自适应控制) 与 Redis 观察窗机制，实现从“即刻阻断”向“风险缓冲”转变。

未知威胁发现：基于 孤立森林 (Isolation Forest) 算法，建立用户行为基线，检测偏离常态的异常访问。

2. 系统总体架构 (System Architecture)

系统遵循 “感知—分析—控制” 的分层闭环设计。

2.1 技术栈选型

开发框架：Spring Boot + MyBatis Plus

核心引擎：Alibaba Druid (SQL Parser)

中间件：

Redis (利用 TTL 实现时间窗口)

MySQL (存储审计日志)

算法模块：Java ML / Python (Isolation Forest)

前端交互：Vue.js + Apache ECharts

2.2 模块划分

感知层 (Perception)：负责流量捕获与协议解析。

SQL 采集器：基于 MyBatis Interceptor 实现无侵入式采集。

语义解析器：基于 AST（抽象语法树）提取表名、字段与操作类型。

分析层 (Analysis)：负责风险计算与模型推理。

DLP 扫描引擎：对 ResultSet 进行正则匹配与敏感度打分。

异常检测模型：基于无监督学习分析行为特征（时间、频率、熵值）。

控制层 (Control)：负责状态流转与响应。

状态机 (State Machine)：维护用户风险状态（正常/观察/阻断）。

执行器：执行连接切断、告警或放行操作。

3. 详细功能设计 (Detailed Design)

3.1 核心模块一：全链路审计与语义解析

解决“身份断层”问题，将 SQL 与应用层用户绑定。

实现机制：

拦截：在 MyBatis Executor 层拦截 SQL。

解析：使用 Druid Parser 生成 AST，提取元数据，而非简单的正则匹配。

绑定：从 ThreadLocal 或 Session 中获取当前 App_User_ID，写入日志。

3.2 核心模块二：敏感数据流式扫描 (DLP)

解决“管管道不管水流”的问题，量化数据泄露风险。

OOM 防护策略：

流式处理：严禁全量加载 ResultSet 到内存，使用 ResultHandler 逐行处理。

随机采样：对于超大结果集（>1000行），仅扫描前 100 行 + 随机 50 行，估算整体风险。

评分逻辑：


$$RiskScore = \sum (FieldWeight \times MatchCount)$$

3.3 核心模块三：弹性风险控制状态机 (RAdAC)

打破“非黑即白”，提供“风险观察窗”。

Redis Key 设计：

sec:status:{userId}：存储状态（NORMAL / OBSERVING / BLOCKED）。

TTL 机制：设置 Key 过期时间为 5 分钟（300s），利用 Redis Key Expiration Event 触发状态重置或升级。

状态流转逻辑：

Normal：评分 < 阈值，放行。

Observing (观察期)：低风险异常 -> 写入 Redis (TTL 5min)。允许用户修正，前端弹出警告。

Blocked (阻断)：观察期内再次违规 OR 触发高危操作 -> 阻断连接。

4. 数据库设计 (Database Design)

4.1 审计日志表 (sys_audit_log)

存储完整的访问上下文，用于溯源与 AI 训练。

字段名

类型

说明

来源

trace_id

CHAR(32)

全局唯一 ID

UUID

app_user_id

VARCHAR(64)

应用层用户 ID

Session (解决身份断层)

sql_template

TEXT

脱敏后的 SQL 模版

AST Parser

table_names

VARCHAR(255)

访问表集合

AST Parser

risk_score

INT

DLP 风险评分

DLP Engine

result_count

INT

结果集行数

ResultSet

action_taken

VARCHAR(20)

最终处置 (PASS/BLOCK)

RAdAC State Machine

create_time

DATETIME

访问时间

System

4.2 风险规则表 (sys_risk_rule)

字段名

类型

说明

示例

rule_name

VARCHAR

规则名称

手机号识别

regex

VARCHAR

正则表达式

1[3-9]\d{9}

score

INT

单次命中分数

10

5. 核心算法与代码骨架示例 (Core Implementation)

5.1 MyBatis 拦截器骨架

@Intercepts({@Signature(type = Executor.class, method = "query", ...)})
public class SqlAuditInterceptor implements Interceptor {
    public Object intercept(Invocation invocation) throws Throwable {
        // 1. 获取开始时间与原始 SQL
        long start = System.currentTimeMillis();
        
        // 2. 执行原查询 (获取 ResultSet)
        Object result = invocation.proceed();
        
        // 3. 异步分析 (避免阻塞主业务)
        CompletableFuture.runAsync(() -> {
            // A. 解析 SQL 提取表名 (AST)
            // B. 扫描 Result 进行 DLP 评分 (Stream/Sample)
            // C. 检查 Redis 状态机决定是否告警/阻断
            // D. 记录日志入库
        });
        
        return result;
    }
}


5.2 数据交互格式 (JSON)

用于后端与 AI 模块或前端大屏的数据交互。

{
  "traceId": "uuid-1234",
  "identity": { "appUserId": "user_007", "ip": "10.0.0.1" },
  "behavior": {
    "tables": ["users", "orders"],
    "operation": "SELECT",
    "rows": 5000
  },
  "risk": {
    "score": 85,
    "status": "OBSERVING" 
  }
}


6. 开发实施计划 (Roadmap)

根据开题报告时间表进行任务拆解：

阶段一：基础设施搭建 (2025.12 - 2026.01)

完成 Spring Boot 工程搭建。

里程碑：实现 MyBatis 拦截器，能打印出 SQL 日志。

阶段二：核心引擎开发 (2026.01 - 2026.03)

集成 Druid Parser，实现 SQL 语义解析（提取表名、字段）。

实现 DLP 正则扫描与采样逻辑。

里程碑：系统能正确识别“敏感表查询”并记录评分。

阶段三：弹性控制与 AI (2026.03 - 2026.04)

开发 Redis 状态机（TTL 逻辑）。

采集样本数据，训练孤立森林模型。

里程碑：实现“观察窗”功能，即首次违规告警，二次违规阻断。

阶段四：测试与论文 (2026.04 - 2026.05)

压力测试（重点测试 DLP 模块的内存占用）。

撰写毕业论文。
