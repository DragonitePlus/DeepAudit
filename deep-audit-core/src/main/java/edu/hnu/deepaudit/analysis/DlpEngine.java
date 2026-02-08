package edu.hnu.deepaudit.analysis;

import edu.hnu.deepaudit.model.SysSensitiveTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DLP Engine: Handles Sensitive Data Access Risk Calculation
 */
public class DlpEngine {

    private static final Logger log = LoggerFactory.getLogger(DlpEngine.class);

    // 内存缓存：表名 -> 配置
    private final Map<String, SysSensitiveTable> sensitiveTableMap = new HashMap<>();

    /**
     * 初始化/刷新敏感表配置
     */
    public void setSensitiveTables(List<SysSensitiveTable> tables) {
        sensitiveTableMap.clear();
        if (tables != null) {
            for (SysSensitiveTable table : tables) {
                // 使用小写存储，支持大小写不敏感匹配
                sensitiveTableMap.put(table.getTableName().toLowerCase(), table);
            }
        }
        log.info("DlpEngine loaded {} sensitive tables.", sensitiveTableMap.size());
    }

    /**
     * 计算风险分
     * @param tableNames 解析出的表名集合
     * @return 最高风险分
     */
    public int calculateRiskScore(Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }

        double maxRisk = 0.0;

        for (String tableName : tableNames) {
            String lowerName = tableName.toLowerCase();
            // 去除可能的库名前缀 (e.g. db.table -> table)
            if (lowerName.contains(".")) {
                lowerName = lowerName.substring(lowerName.lastIndexOf(".") + 1);
            }

            // 简单去除反引号 (e.g. `table` -> table)
            lowerName = lowerName.replace("`", "");

            if (sensitiveTableMap.containsKey(lowerName)) {
                SysSensitiveTable config = sensitiveTableMap.get(lowerName);

                // 1. 确定基础分 (Base Score)
                // 默认策略：级别 * 15 (Level 1=15, Level 4=60)
                int baseScore = config.getSensitivityLevel() * 15;

                // 2. 应用系数 (Coefficient)
                // e.g. Base 30 * Coeff 2.0 = 60
                double score = baseScore * config.getCoefficient();

                maxRisk = Math.max(maxRisk, score);
            }
        }

        return (int) Math.min(maxRisk, 100); // 上限 100
    }
}