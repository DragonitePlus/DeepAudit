package edu.hnu.deepaudit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
}
