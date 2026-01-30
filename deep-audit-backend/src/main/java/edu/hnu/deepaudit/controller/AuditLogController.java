package edu.hnu.deepaudit.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;
import edu.hnu.deepaudit.model.SysAuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @GetMapping("/trend")
    public List<Map<String, Object>> getRiskTrend() {
        return sysAuditLogMapper.selectRiskTrend();
    }

    /**
     * Paged Audit Logs
     */
    @GetMapping("/logs")
    public Page<SysAuditLog> getAuditLogs(@RequestParam(defaultValue = "1") int current,
                                          @RequestParam(defaultValue = "10") int size) {
        Page<SysAuditLog> page = new Page<>(current, size);
        return sysAuditLogMapper.selectPage(page, new QueryWrapper<SysAuditLog>().orderByDesc("create_time"));
    }

    /**
     * Submit Feedback
     */
    @PostMapping("/feedback")
    public String submitFeedback(@RequestBody Map<String, Object> body) {
        String traceId = (String) body.get("traceId");
        Integer status = (Integer) body.get("status");
        
        if (traceId == null || status == null) {
            throw new IllegalArgumentException("TraceId and status are required");
        }
        
        SysAuditLog log = sysAuditLogMapper.selectById(traceId);
        if (log != null) {
            log.setFeedbackStatus(status);
            sysAuditLogMapper.updateById(log);
            return "Feedback submitted successfully";
        }
        return "Log not found";
    }
}
