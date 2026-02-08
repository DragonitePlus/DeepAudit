package edu.hnu.deepaudit.analysis;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

public class FeatureExtractor {

    /**
     * 将原始审计数据转换为模型需要的 float[] 特征向量
     * 对应 Python 中的 preprocess_features 逻辑
     * ORDER MUST MATCH: 
     * ['hour_of_day', 'is_workday', 'freq_1min', 'sql_type_weight',
     *  'condition_count', 'join_count', 'nested_level', 'has_always_true']
     */
    public static float[] extractFeatures(LocalDateTime timestamp,
                                          int freq1Min, int sqlTypeWeight,
                                          int conditionCount, int joinCount, int nestedLevel, boolean hasAlwaysTrue) {
        
        // 1. hour_of_day
        float hourOfDay = (float) timestamp.getHour();

        // 2. is_workday
        int dayVal = timestamp.getDayOfWeek().getValue();
        float isWorkday = (dayVal <= 5) ? 1.0f : 0.0f;

        // 3. freq_1min & sql_type_weight
        float f_freq1Min = (float) freq1Min;
        float f_sqlTypeWeight = (float) sqlTypeWeight;

        // 4. AST Features
        float f_conditionCount = (float) conditionCount;
        float f_joinCount = (float) joinCount;
        float f_nestedLevel = (float) nestedLevel;
        float f_hasAlwaysTrue = hasAlwaysTrue ? 1.0f : 0.0f;

        return new float[] {
            hourOfDay, isWorkday,
            f_freq1Min, f_sqlTypeWeight,
            f_conditionCount, f_joinCount, f_nestedLevel, f_hasAlwaysTrue
        };
    }
}
