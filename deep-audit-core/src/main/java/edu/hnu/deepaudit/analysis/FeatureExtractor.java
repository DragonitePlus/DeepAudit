package edu.hnu.deepaudit.analysis;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

public class FeatureExtractor {

    /**
     * 将原始审计数据转换为模型需要的 float[] 特征向量
     * 对应 Python 中的 preprocess_features 逻辑
     */
    public static float[] extractFeatures(LocalDateTime timestamp, long rowCount, long execTimeMs,
                                          int sqlLength, int numTables, int numJoins, int freq1Min) {
        
        // 1. hour_of_day (Python: df['timestamp'].dt.hour)
        float hourOfDay = (float) timestamp.getHour();

        // 2. is_workday (Python: x < 5 else 0)
        // Java DayOfWeek: MONDAY(1) ... SUNDAY(7)
        int dayVal = timestamp.getDayOfWeek().getValue();
        float isWorkday = (dayVal <= 5) ? 1.0f : 0.0f;

        // 3. log_row_count (Python: np.log1p)
        // Math.log1p(x) 等价于 ln(x + 1)
        float logRowCount = (float) Math.log1p(rowCount);

        // 4. log_exec_time (Python: np.log1p)
        float logExecTime = (float) Math.log1p(execTimeMs);

        // 5. 其他直接特征
        float f_sqlLength = (float) sqlLength;
        float f_numTables = (float) numTables;
        float f_numJoins = (float) numJoins;
        float f_freq1Min = (float) freq1Min;

        // 🔥 必须严格按照 Python 训练时的列顺序组装数组！
        // ['hour_of_day', 'is_workday', 'log_row_count', 'log_exec_time', 
        //  'sql_length', 'num_tables', 'num_joins', 'freq_1min']
        return new float[] {
            hourOfDay, isWorkday, logRowCount, logExecTime,
            f_sqlLength, f_numTables, f_numJoins, f_freq1Min
        };
    }
}
