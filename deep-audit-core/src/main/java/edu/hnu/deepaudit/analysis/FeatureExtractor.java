package edu.hnu.deepaudit.analysis;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

public class FeatureExtractor {

    /**
     * 将原始审计数据转换为模型需要的 float[] 特征向量
     * 对应 Python 中的 preprocess_features 逻辑
     * ORDER MUST MATCH: 
     * ['hour_of_day', 'is_workday', 'log_row_count', 'log_affected_rows', 'log_exec_time', 'freq_1min', 'sql_type_weight',
     *  'condition_count', 'join_count', 'nested_level', 'has_always_true', 'client_app_risk', 'error_code_risk']
     */
    public static float[] extractFeatures(LocalDateTime timestamp, long rowCount, long affectedRows, long execTimeMs,
                                          int freq1Min, int sqlTypeWeight,
                                          int conditionCount, int joinCount, int nestedLevel, boolean hasAlwaysTrue,
                                          int clientAppRisk, int errorCodeRisk) {
        
        // 1. hour_of_day
        float hourOfDay = (float) timestamp.getHour();

        // 2. is_workday
        int dayVal = timestamp.getDayOfWeek().getValue();
        float isWorkday = (dayVal <= 5) ? 1.0f : 0.0f;

        // 3. log_row_count
        float logRowCount = (float) Math.log1p(rowCount);

        // 4. log_affected_rows
        float logAffectedRows = (float) Math.log1p(affectedRows);

        // 5. log_exec_time
        float logExecTime = (float) Math.log1p(execTimeMs);

        // Direct mappings
        float f_freq1Min = (float) freq1Min;
        float f_sqlTypeWeight = (float) sqlTypeWeight;
        float f_conditionCount = (float) conditionCount;
        float f_joinCount = (float) joinCount;
        float f_nestedLevel = (float) nestedLevel;
        float f_hasAlwaysTrue = hasAlwaysTrue ? 1.0f : 0.0f;
        float f_clientAppRisk = (float) clientAppRisk;
        float f_errorCodeRisk = (float) errorCodeRisk;

        return new float[] {
            hourOfDay, isWorkday, logRowCount, logAffectedRows, logExecTime,
            f_freq1Min, f_sqlTypeWeight,
            f_conditionCount, f_joinCount, f_nestedLevel, f_hasAlwaysTrue,
            f_clientAppRisk, f_errorCodeRisk
        };
    }
}
