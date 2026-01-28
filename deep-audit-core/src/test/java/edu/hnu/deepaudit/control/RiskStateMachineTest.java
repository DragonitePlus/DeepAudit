package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 风险状态机单元测试
 * 测试思想：
 * 1. 使用 Mockito 模拟 Redis 和 Database 依赖，不依赖真实环境。
 * 2. 验证状态机能否正确解析 Redis Lua 脚本的返回结果。
 * 3. 确保在不同风险分下（正常、警告、阻断）返回正确的动作。
 */
@ExtendWith(MockitoExtension.class)
class RiskStateMachineTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RiskProperties riskProperties;

    @Mock
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    // POJO under test
    private RiskStateMachine riskStateMachine;

    @BeforeEach
    void setUp() {
        // 手动注入依赖 (Setter Injection)
        riskStateMachine = new RiskStateMachine();
        riskStateMachine.setRedisTemplate(redisTemplate);
        riskStateMachine.setRiskProperties(riskProperties);
        riskStateMachine.setSysUserRiskProfileMapper(sysUserRiskProfileMapper);
        
        // Setup properties
        when(riskProperties.getDecayRate()).thenReturn(0.5);
        when(riskProperties.getObservationThreshold()).thenReturn(40);
        when(riskProperties.getBlockThreshold()).thenReturn(100);
        when(riskProperties.getWindowTtl()).thenReturn(300);
        
        // Init script
        riskStateMachine.init();
    }

    @Test
    void testCheckStatus_Normal() {
        // 测试正常状态
        // Mock Redis return: ["NORMAL", "10", "ALLOW"]
        List<String> mockResult = Arrays.asList("NORMAL", "10", "ALLOW");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 10);
        
        Assertions.assertEquals("ALLOW", action);
    }

    @Test
    void testCheckStatus_Block() {
        // 测试阻断状态
        // Mock Redis return: ["BLOCKED", "110", "BLOCK"]
        List<String> mockResult = Arrays.asList("BLOCKED", "110", "BLOCK");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 100);
        
        Assertions.assertEquals("BLOCK", action);
    }

    @Test
    void testCheckStatus_Warning() {
        // 测试警告/观察状态
        // Mock Redis return: ["OBSERVATION", "50", "WARNING"]
        List<String> mockResult = Arrays.asList("OBSERVATION", "50", "WARNING");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 50);
        
        Assertions.assertEquals("WARNING", action);
    }
}
