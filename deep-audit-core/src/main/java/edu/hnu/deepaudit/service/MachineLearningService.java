package edu.hnu.deepaudit.service;

import edu.hnu.deepaudit.model.SysAuditLog;

/**
 * 机器学习服务接口
 * 用于异常检测和行为分析
 */
public interface MachineLearningService {

    /**
     * 计算异常分数
     * @param log 审计日志
     * @return 异常分数 (0.0 - 1.0)
     */
    double calculateAnomalyScore(SysAuditLog log);

    /**
     * 训练模型
     * @return 训练结果
     */
    String trainModel();
}
