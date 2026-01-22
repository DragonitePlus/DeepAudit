from flask import Flask, request, jsonify
import joblib
import pandas as pd
import numpy as np
from datetime import datetime
import os

app = Flask(__name__)

# 加载训练好的模型
MODEL_PATH = 'db_audit_iso_forest.joblib'
MODEL = None

if os.path.exists(MODEL_PATH):
    MODEL = joblib.load(MODEL_PATH)
    print("Model loaded successfully.")
else:
    print("Model file not found. Please run training script first.")

@app.route('/predict_risk', methods=['POST'])
def predict_risk():
    if MODEL is None:
        return jsonify({"status": "error", "message": "Model not loaded"}), 503

    try:
        data = request.json
        
        # 1. 接收 Java 传来的原始数据 
        # 格式示例: {"timestamp": 1709234000, "row_count": 5000, "exec_time": 200...} 
        input_data = {
            'timestamp': pd.to_datetime(data['timestamp'], unit='ms'), # Java 传毫秒级时间戳
            'row_count': data.get('row_count', 0),
            'exec_time': data.get('exec_time', 0),
            'sql_length': data.get('sql_length', 0),
            'num_tables': data.get('num_tables', 0),
            'num_joins': data.get('num_joins', 0),
            'freq_1min': data.get('freq_1min', 0)
        }
        
        df = pd.DataFrame([input_data])
        
        # 2. 实时特征转换 (同训练逻辑) 
        df['hour_of_day'] = df['timestamp'].dt.hour
        df['is_workday'] = df['timestamp'].dt.dayofweek.apply(lambda x: 1 if x < 5 else 0)
        df['log_row_count'] = np.log1p(df['row_count'])
        df['log_exec_time'] = np.log1p(df['exec_time'])
        
        features = df[[ 
            'hour_of_day', 'is_workday', 
            'log_row_count', 'log_exec_time', 
            'sql_length', 'num_tables', 'num_joins', 
            'freq_1min' 
        ]]

        # 3. 获取原始异常分 (decision_function) 
        # scikit-learn 中: 正数代表正常，负数代表异常 
        raw_scores = MODEL.decision_function(features)
        raw_score = raw_scores[0]
        
        # 4. 实现论文 的风险归一化公式 
        # S_anomaly = 0 if score > 0 (Normal) 
        # S_anomaly = |score| * 50 if score < 0 (Anomaly) 
        if raw_score > 0:
            final_risk_score = 0.0
        else:
            final_risk_score = abs(raw_score) * 50.0
            # 截断最高分为 100 
            final_risk_score = min(final_risk_score, 100.0)

        return jsonify({
            "status": "success",
            "is_anomaly": bool(raw_score < 0),
            "raw_score": float(raw_score),
            "normalized_risk_score": float(final_risk_score) # 这是 S_anomaly
        })

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    # 生产环境建议使用 gunicorn 
    app.run(host='0.0.0.0', port=5000)
