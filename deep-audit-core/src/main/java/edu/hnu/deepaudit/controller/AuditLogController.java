package edu.hnu.deepaudit.controller;

import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
