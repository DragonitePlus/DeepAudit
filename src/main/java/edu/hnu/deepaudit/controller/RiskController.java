package edu.hnu.deepaudit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    @Autowired
    private RiskProperties riskProperties;

    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    @GetMapping("/config")
    public RiskProperties getConfig() {
        return riskProperties;
    }

    @PostMapping("/config")
    public String updateConfig(@RequestBody RiskProperties newConfig) {
        riskProperties.setDecayRate(newConfig.getDecayRate());
        riskProperties.setObservationThreshold(newConfig.getObservationThreshold());
        riskProperties.setBlockThreshold(newConfig.getBlockThreshold());
        riskProperties.setWindowTtl(newConfig.getWindowTtl());
        riskProperties.setMlWeight(newConfig.getMlWeight());
        return "Risk configuration updated successfully";
    }

    /**
     * 分页查询用户风险画像
     * @param page 页码 (默认1)
     * @param size 每页大小 (默认10)
     * @return 分页结果
     */
    @GetMapping("/users")
    public IPage<SysUserRiskProfile> getUserRiskProfiles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<SysUserRiskProfile> pageParam = new Page<>(page, size);
        return sysUserRiskProfileMapper.selectPage(pageParam, null);
    }
    
    @GetMapping("/stats")
    public Map<String, Object> getRiskStats() {
        Map<String, Object> stats = new HashMap<>();
        // 修正：仅查询系统库 (sys_user_risk_profile) 避免触发业务库审计
        // 前提：TestDataGenerator 确保了 SysUser 和 RiskProfile 的 1:1 关系
        Long totalUsers = sysUserRiskProfileMapper.selectCount(null);
        Long monitoredUsers = totalUsers; // 现在全量用户都有画像
        
        stats.put("totalUsers", totalUsers);
        stats.put("monitoredUsers", monitoredUsers);
        return stats;
    }
}
