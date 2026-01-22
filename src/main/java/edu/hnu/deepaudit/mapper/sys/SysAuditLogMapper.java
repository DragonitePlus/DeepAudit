package edu.hnu.deepaudit.mapper.sys;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.hnu.deepaudit.model.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    @Select("SELECT DATE_FORMAT(create_time, '%H:%i') as timeSlot, SUM(risk_score) as totalScore " +
            "FROM sys_audit_log " +
            "WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "GROUP BY timeSlot " +
            "ORDER BY timeSlot")
    List<Map<String, Object>> selectRiskTrend();
}
