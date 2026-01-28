import pandas as pd
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
import joblib
import random
from datetime import datetime, timedelta

# 引入 ONNX 转换库
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# ==========================================
# 1. 模拟数据生成 (基于论文 Source 113-117)
# ==========================================
def generate_mock_data(n_samples=5000):
    data = []
    base_time = datetime.now()
    
    for _ in range(n_samples):
        # 模拟：90% 是正常操作，10% 是异常
        is_anomaly = random.random() < 0.1
        
        # 1. 时间特征
        # 正常操作多在工作时间 (9-18)，异常可能在深夜
        if is_anomaly and random.random() > 0.5:
            dt = base_time - timedelta(hours=random.randint(0, 24))
        else:
            # 倾向于工作时间
            dt = base_time.replace(hour=random.randint(9, 18))
            
        # 2. 量级特征
        # 异常操作往往涉及大量行数 (如拖库)
        if is_anomaly:
            row_count = random.randint(1000, 1000000)
            exec_time = random.randint(1000, 60000) # ms
        else:
            row_count = random.randint(0, 100)
            exec_time = random.randint(1, 500)

        # 3. 结构特征
        # 复杂的关联查询或超长 SQL
        if is_anomaly:
            sql_len = random.randint(500, 5000)
            num_tables = random.randint(3, 10)
            num_joins = random.randint(2, 8)
        else:
            sql_len = random.randint(20, 200)
            num_tables = 1
            num_joins = 0

        # 4. 频率特征
        # 过去1分钟内的调用次数 (需在Java侧聚合，这里模拟数值)
        freq_1min = random.randint(50, 200) if is_anomaly else random.randint(0, 10)

        data.append({
            'timestamp': dt,
            'row_count': row_count,
            'exec_time': exec_time,
            'sql_length': sql_len,
            'num_tables': num_tables,
            'num_joins': num_joins,
            'freq_1min': freq_1min
        })
    
    return pd.DataFrame(data)

# ==========================================
# 2. 特征工程处理
# ==========================================
def preprocess_features(df):
    # 提取 hour_of_day
    df['hour_of_day'] = df['timestamp'].dt.hour
    
    # 提取 is_workday (0/1)
    # 简单逻辑：周一(0)到周五(4)为工作日
    df['is_workday'] = df['timestamp'].dt.dayofweek.apply(lambda x: 1 if x < 5 else 0)
    
    # 量级特征对数化处理
    # 使用 log1p 避免 log(0)
    df['log_row_count'] = np.log1p(df['row_count'])
    df['log_exec_time'] = np.log1p(df['exec_time'])
    
    # 选定最终用于训练的特征向量
    feature_cols = [
        'hour_of_day', 'is_workday',
        'log_row_count', 'log_exec_time',
        'sql_length', 'num_tables', 'num_joins',
        'freq_1min'
    ]
    return df[feature_cols]

# ==========================================
# 3. 模型训练与保存
# ==========================================
def train_and_save():
    print("Generating mock data...")
    raw_df = generate_mock_data()

    print("Preprocessing features...")
    X = preprocess_features(raw_df)

    # 孤立森林参数建议
    clf = IsolationForest(
        n_estimators=100,
        max_samples='auto',
        contamination=0.05,
        random_state=42,
        n_jobs=-1
    )

    model_pipeline = Pipeline([
        ('scaler', StandardScaler()),
        ('iso_forest', clf)
    ])

    print("Training Isolation Forest model...")
    model_pipeline.fit(X)

    # 保存 Joblib 模型 (Python 使用)
    joblib.dump(model_pipeline, 'db_audit_iso_forest.joblib')

    # ==========================================
    # 🔥 关键修改：导出为 ONNX
    # ==========================================
    print("Converting to ONNX...")

    # 1. 定义输入类型
    n_features = 8
    initial_type = [('float_input', FloatTensorType([None, n_features]))]

    # 2. 转换 (修复了报错的部分)
    # target_opset={'': 12, 'ai.onnx.ml': 3}
    # '': 12 代表通用算子(Add, MatMul等)使用版本 12
    # 'ai.onnx.ml': 3 代表机器学习算子(TreeEnsemble等)强制使用版本 3
    onnx_model = convert_sklearn(
        model_pipeline,
        initial_types=initial_type,
        target_opset={'': 12, 'ai.onnx.ml': 3}
    )

    # 3. 保存
    onnx_path = 'deep_audit_iso_forest.onnx'
    with open(onnx_path, "wb") as f:
        f.write(onnx_model.SerializeToString())

    print(f"ONNX Model saved to {onnx_path}")

if __name__ == "__main__":
    train_and_save()
