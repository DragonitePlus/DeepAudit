import pandas as pd
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
import joblib
import json
import random
import re
from datetime import datetime, timedelta
from sqlalchemy import create_engine
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# ==========================================
# 0. 配置部分 (Configuration)
# ==========================================
# 数据库连接，用于拉取真实反馈数据 (Feedback Loop)
DB_CONNECTION_STR = "mysql+pymysql://root:root@127.0.0.1:3306/deepaudit_sys"

# 训练样本规模 (Large Scale)
N_SAMPLES = 100000

# ==========================================
# 1. 科学模拟数据生成 (Scientific Mock Data)
# ==========================================
def generate_mock_data(n_samples=50000):
    """
    生成符合真实数据库负载分布的模拟数据。
    包含 95% 正常流量 + 5% 攻击流量 (覆盖 5 大攻击场景)。
    """
    print(f"DeepAudit: Generating {n_samples} samples with comprehensive attack scenarios...")

    n_normal = int(n_samples * 0.95)
    n_anomaly = n_samples - n_normal
    base_time = datetime.now()

    data = []

    # -------------------------------------------------------------------------
    # A. 正常流量 (Normal Behavior - 95%)
    # 特征：低复杂度、工作时间、无错误、标准客户端
    # -------------------------------------------------------------------------
    # 1. 时间分布：高斯分布，集中在 14:00 (sigma=3h)
    hours_normal = np.random.normal(loc=14, scale=3, size=n_normal)
    hours_normal = np.clip(hours_normal, 0, 23).astype(int)

    # 2. 查询行数 (SELECT)：对数正态分布 (大部分查询只返回几行)
    rows_normal = np.random.lognormal(mean=2, sigma=1.2, size=n_normal).astype(int)

    # 3. 影响行数 (DML)：90% 为 0 (读操作)，10% 为写操作 (少量行)
    is_write = np.random.random(n_normal) < 0.1
    affected_normal = np.zeros(n_normal)
    affected_normal[is_write] = np.random.lognormal(mean=0.5, sigma=0.8, size=np.sum(is_write))

    # 4. AST 结构特征 (正常 SQL 通常很简单)
    # condition_count: WHERE 条件数 (泊松分布, lambda=1)
    cond_count_normal = np.random.poisson(lam=1, size=n_normal)
    # join_count: 连接表数 (大部分为 0 或 1)
    join_count_normal = np.random.choice([0, 1, 2], size=n_normal, p=[0.7, 0.25, 0.05])
    # nested_level: 嵌套层级 (0)
    nested_normal = np.zeros(n_normal)
    # has_always_true: 是否包含 1=1 (绝对没有)
    always_true_normal = np.zeros(n_normal)

    # 5. 客户端特征 (0=正常客户端, 1=异常脚本)
    client_risk_normal = np.zeros(n_normal)

    # 6. 耗时 & 频率
    exec_time_normal = np.random.lognormal(mean=3, sigma=1.0, size=n_normal).astype(int) # ms
    freq_normal = np.random.poisson(lam=5, size=n_normal)

    # 7. 错误码 (0=成功)
    error_risk_normal = np.zeros(n_normal)

    # 生成正常数据集
    for i in range(n_normal):
        dt = base_time - timedelta(days=random.randint(0, 7))
        dt = dt.replace(hour=hours_normal[i], minute=random.randint(0, 59))

        # SQL 类型权重: 写操作=3, 读操作=1
        w = 3 if is_write[i] else 1

        data.append({
            'timestamp': dt,
            'row_count': rows_normal[i],
            'affected_rows': int(affected_normal[i]),
            'exec_time': exec_time_normal[i],
            'sql_type_weight': w,
            'freq_1min': freq_normal[i],
            # AST 特征
            'condition_count': cond_count_normal[i],
            'join_count': join_count_normal[i],
            'nested_level': int(nested_normal[i]),
            'has_always_true': int(always_true_normal[i]),
            # 环境特征
            'client_app_risk': int(client_risk_normal[i]),
            'error_code_risk': int(error_risk_normal[i]),
            'label': 0
        })

    # -------------------------------------------------------------------------
    # B. 异常流量 (Anomaly Scenarios - 5%)
    # 模拟真实攻击场景，训练模型识别这些模式
    # -------------------------------------------------------------------------

    # 场景1: SQL 注入 (Boolean Injection)
    # 特征: 包含 1=1, 高条件数, 可能有错误
    for _ in range(int(n_anomaly * 0.2)):
        data.append({
            'timestamp': base_time,
            'row_count': random.randint(0, 100),
            'affected_rows': 0,
            'exec_time': random.randint(10, 200),
            'sql_type_weight': 1,
            'freq_1min': random.randint(10, 50),
            # AST 异常
            'condition_count': random.randint(5, 10), # 复杂条件
            'join_count': 0,
            'nested_level': 0,
            'has_always_true': 1, # 🔥 致命特征 (1=1)
            'client_app_risk': 1, # 可能使用 sqlmap
            'error_code_risk': random.choice([0, 1]),
            'label': 1
        })

    # 场景2: 拖库 (Data Exfiltration)
    # 特征: 巨大 row_count, 深夜访问
    for _ in range(int(n_anomaly * 0.2)):
        data.append({
            'timestamp': base_time.replace(hour=3), # 深夜
            'row_count': np.random.randint(50000, 1000000), # 🔥 拖库
            'affected_rows': 0,
            'exec_time': np.random.randint(5000, 60000),
            'sql_type_weight': 1,
            'freq_1min': random.randint(1, 5),
            'condition_count': 1,
            'join_count': 0,
            'nested_level': 0,
            'has_always_true': 0,
            'client_app_risk': 0,
            'error_code_risk': 0,
            'label': 1
        })

    # 场景3: 恶意删改 (Destructive Operation)
    # 特征: 巨大 affected_rows, 高权重类型(DDL/DML)
    for _ in range(int(n_anomaly * 0.2)):
        data.append({
            'timestamp': base_time,
            'row_count': 0,
            'affected_rows': np.random.randint(1000, 50000), # 🔥 删库
            'exec_time': np.random.randint(1000, 10000),
            'sql_type_weight': 5, # DDL/高危
            'freq_1min': random.randint(1, 5),
            'condition_count': 1,
            'join_count': 0,
            'nested_level': 0,
            'has_always_true': 0,
            'client_app_risk': 0,
            'error_code_risk': 0,
            'label': 1
        })

    # 场景4: 慢查询 DoS (Denial of Service)
    # 特征: 极高 exec_time, 高 join_count, 高嵌套
    for _ in range(int(n_anomaly * 0.2)):
        data.append({
            'timestamp': base_time,
            'row_count': 100,
            'affected_rows': 0,
            'exec_time': np.random.randint(30000, 100000), # 🔥 30s+
            'sql_type_weight': 1,
            'freq_1min': random.randint(1, 5),
            'condition_count': random.randint(5, 20),
            'join_count': random.randint(5, 10), # 🔥 多表关联
            'nested_level': random.randint(2, 5), # 🔥 嵌套查询
            'has_always_true': 0,
            'client_app_risk': 0,
            'error_code_risk': 0,
            'label': 1
        })

    # 场景5: 暴力探测 (Brute Force / Scanning)
    # 特征: 高频, 高错误率, 脚本客户端
    for _ in range(int(n_anomaly * 0.2)):
        data.append({
            'timestamp': base_time,
            'row_count': 0,
            'affected_rows': 0,
            'exec_time': random.randint(1, 10),
            'sql_type_weight': 1,
            'freq_1min': np.random.randint(100, 500), # 🔥 极高频
            'condition_count': 0,
            'join_count': 0,
            'nested_level': 0,
            'has_always_true': 0,
            'client_app_risk': 1, # 🔥 脚本工具
            'error_code_risk': 1, # 🔥 频繁报错
            'label': 1
        })

    df = pd.DataFrame(data)
    # 混洗数据
    df = df.sample(frac=1).reset_index(drop=True)
    return df

# ==========================================
# 2. 从数据库提取真实数据 (ETL)
# ==========================================
def extract_sql_features_simple(sql):
    """简单的 SQL 特征提取 (用于从数据库恢复特征)"""
    if not sql: return 0, 0, 0, 0
    s = str(sql).lower()
    cond = s.count(' and ') + s.count(' or ') + s.count(' where ')
    join = s.count(' join ')
    nested = s.count(' select ') - 1
    injection = 1 if '1=1' in s or '1 = 1' in s else 0
    return cond, join, max(0, nested), injection

def fetch_real_data_from_db():
    print("DeepAudit: Connecting to database to fetch real feedback data...")
    try:
        engine = create_engine(DB_CONNECTION_STR)

        # 只读取人工标记为"正常"(feedback_status=1)的数据作为正样本
        query = """
        SELECT create_time, result_count, affected_rows, execution_time, 
               error_code, sql_template, app_user_id, client_app, action_taken
        FROM sys_audit_log
        WHERE feedback_status = 1
        LIMIT 10000
        """
        df = pd.read_sql(query, engine)

        if df.empty:
            print("DeepAudit: No real feedback data found. Using mock data only.")
            return pd.DataFrame()

        print(f"DeepAudit: Fetched {len(df)} rows of real feedback data.")

        # 字段映射与填充
        df['timestamp'] = pd.to_datetime(df['create_time'])
        df['row_count'] = df['result_count'].fillna(0)
        df['affected_rows'] = df['affected_rows'].fillna(0)
        df['exec_time'] = df['execution_time'].fillna(0)

        # 解析 SQL 特征 (模拟 Java 端的 AST 解析)
        feats = df['sql_template'].apply(extract_sql_features_simple)
        df['condition_count'] = [x[0] for x in feats]
        df['join_count'] = [x[1] for x in feats]
        df['nested_level'] = [x[2] for x in feats]
        df['has_always_true'] = [x[3] for x in feats]

        # 解析客户端风险 (简单规则: python/curl/sqlmap 视为高危)
        def get_client_risk(app):
            app = str(app).lower()
            if 'python' in app or 'curl' in app or 'sqlmap' in app: return 1
            return 0
        df['client_app_risk'] = df['client_app'].apply(get_client_risk)

        # 错误码风险
        df['error_code_risk'] = df['error_code'].apply(lambda x: 1 if x and x > 0 else 0)

        # SQL 类型权重
        def get_type_weight(sql):
            s = str(sql).lower()
            if 'drop ' in s or 'truncate ' in s or 'grant ' in s: return 5
            if 'update ' in s or 'delete ' in s or 'insert ' in s: return 3
            return 1
        df['sql_type_weight'] = df['sql_template'].apply(get_type_weight)

        # 计算 freq_1min
        df = df.sort_values('timestamp')
        freq = df.set_index('timestamp').groupby('app_user_id').rolling('1min')['sql_template'].count().reset_index()
        df['freq_1min'] = freq['sql_template'].values

        df['label'] = 0
        return df
    except Exception as e:
        print(f"DeepAudit DB Error: {e}")
        return pd.DataFrame()

# ==========================================
# 3. 特征工程处理 (Feature Engineering)
# ==========================================
def preprocess_features(df):
    if df.empty: return df

    # 1. 时间特征
    df['hour_of_day'] = df['timestamp'].dt.hour
    df['is_workday'] = df['timestamp'].dt.dayofweek.apply(lambda x: 1 if x < 5 else 0)

    # 2. 量级特征对数化 (Log Transform)
    df['log_row_count'] = np.log1p(df['row_count'])
    df['log_affected_rows'] = np.log1p(df['affected_rows'])
    df['log_exec_time'] = np.log1p(df['exec_time'])

    # 3. 选定最终特征列 (共 13 个特征，与 Java FeatureExtractor 必须一致)
    feature_cols = [
        'hour_of_day',
        'is_workday',
        'log_row_count',
        'log_affected_rows',
        'log_exec_time',
        'freq_1min',
        'sql_type_weight',
        # AST 特征
        'condition_count',
        'join_count',
        'nested_level',
        'has_always_true',
        # 环境特征
        'client_app_risk',
        'error_code_risk'
    ]

    return df[feature_cols].fillna(0)

# ==========================================
# 4. 解释规则与打分生成 (Scoring Logic)
# ==========================================
def generate_explanation_rules(df_processed, model, scaler):
    """
    生成包含'扣分逻辑'的解释规则。
    策略:
    - 100分满分 (Trust Score)
    - 扣 40分 -> 观察 (Observation)
    - 扣 100分 -> 阻断 (Block)
    """
    print("DeepAudit: Generating scoring rules based on normal distribution...")

    feature_names = df_processed.columns.tolist()

    # 预测并筛选正常样本 (基准线)
    X_scaled = scaler.transform(df_processed)
    preds = model.predict(X_scaled)
    normal_df = df_processed[preds == 1]

    rules = {}

    # 定义每个特征的扣分策略 (Risk Deduction)
    # deduction: 基础扣分值
    # critical: 是否直接致命 (true = 单次扣100)
    meta = {
        'hour_of_day':       {'desc': "非工作时间访问", 'deduction': 20, 'critical': False},
        'is_workday':        {'desc': "非工作日访问", 'deduction': 10, 'critical': False},
        'log_row_count':     {'desc': "返回行数异常(疑似拖库)", 'deduction': 50, 'critical': False},
        'log_affected_rows': {'desc': "影响行数异常(疑似删改)", 'deduction': 60, 'critical': False},
        'log_exec_time':     {'desc': "执行耗时过长(慢查询)", 'deduction': 30, 'critical': False},
        'freq_1min':         {'desc': "高频访问(疑似扫描)", 'deduction': 20, 'critical': False},
        'sql_type_weight':   {'desc': "高风险SQL类型", 'deduction': 30, 'critical': False},
        'condition_count':   {'desc': "SQL条件过于复杂", 'deduction': 40, 'critical': False},
        'join_count':        {'desc': "多表关联异常", 'deduction': 40, 'critical': False},
        'nested_level':      {'desc': "嵌套层级过深", 'deduction': 30, 'critical': False},
        'has_always_true':   {'desc': "SQL注入特征(1=1)", 'deduction': 100, 'critical': True}, # 🔥 必杀
        'client_app_risk':   {'desc': "非法客户端工具", 'deduction': 80, 'critical': False},
        'error_code_risk':   {'desc': "数据库异常报错", 'deduction': 20, 'critical': False}
    }

    for col in feature_names:
        # 统计正常数据的 99.9% 分位点作为阈值
        upper = normal_df[col].quantile(0.999)
        lower = normal_df[col].quantile(0.001)

        # 特殊处理布尔值特征 (如 has_always_true)
        if col in ['has_always_true', 'client_app_risk', 'error_code_risk']:
            upper = 0.5 # 只要是 1 就是异常

        info = meta.get(col, {'desc': col, 'deduction': 10})

        rules[col] = {
            "desc": info['desc'],
            "max": float(upper),
            "min": float(lower),
            "deduction": info['deduction'], # 建议扣分
            "is_critical": info['critical'] # 是否直接阻断
        }

    return rules

# ==========================================
# 5. 主流程
# ==========================================
def train_and_save():
    # 1. 生成大规模模拟数据
    print("--- Step 1: Generating Data ---")
    mock_df = generate_mock_data(n_samples=N_SAMPLES)

    # 2. 融合真实反馈 (闭环进化)
    print("--- Step 2: Integrating Feedback ---")
    real_df = fetch_real_data_from_db()
    if not real_df.empty:
        # 过采样真实白名单数据，增加其权重
        real_df_weighted = pd.concat([real_df] * 10, ignore_index=True)
        final_df = pd.concat([mock_df, real_df_weighted], ignore_index=True)
    else:
        final_df = mock_df

    print(f"Total training samples: {len(final_df)}")

    # 3. 预处理
    print("--- Step 3: Preprocessing ---")
    X = preprocess_features(final_df)

    # 4. 训练 Isolation Forest
    print("--- Step 4: Training Model ---")
    clf = IsolationForest(
        n_estimators=300, # 增加树的数量提升稳定性
        max_samples='auto',
        contamination=0.05, # 预计异常比例
        random_state=42,
        n_jobs=-1
    )

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    clf.fit(X_scaled)

    model_pipeline = Pipeline([
        ('scaler', scaler),
        ('iso_forest', clf)
    ])

    # 5. 生成打分规则 (Explainability & Scoring)
    print("--- Step 5: Generating Scoring Rules ---")
    rules = generate_explanation_rules(X, clf, scaler)
    with open('model_explanation_rules.json', 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)
    print("Scoring rules saved to 'model_explanation_rules.json'")

    # 6. 导出 ONNX 模型
    print("--- Step 6: Exporting ONNX ---")
    n_features = X.shape[1]
    initial_type = [('float_input', FloatTensorType([None, n_features]))]

    onnx_model = convert_sklearn(
        model_pipeline,
        initial_types=initial_type,
        target_opset={'': 12, 'ai.onnx.ml': 3}
    )

    with open('deep_audit_iso_forest.onnx', "wb") as f:
        f.write(onnx_model.SerializeToString())

    print("✅ Model trained and saved successfully!")

if __name__ == "__main__":
    train_and_save()