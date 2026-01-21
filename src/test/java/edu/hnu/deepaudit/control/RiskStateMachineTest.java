package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.SysUserRiskProfileMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskStateMachineTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RiskProperties riskProperties;

    @Mock
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    @InjectMocks
    private RiskStateMachine riskStateMachine;

    @BeforeEach
    void setUp() {
        // Setup properties
        when(riskProperties.getDecayRate()).thenReturn(0.5);
        when(riskProperties.getObservationThreshold()).thenReturn(40);
        when(riskProperties.getBlockThreshold()).thenReturn(100);
        when(riskProperties.getWindowTtl()).thenReturn(300);
        
        // Init script manually since @PostConstruct isn't called in pure Mockito unit test
        riskStateMachine.init();
    }

    @Test
    void testCheckStatus_Normal() {
        // Mock Redis return: ["NORMAL", "10", "ALLOW"]
        List<String> mockResult = Arrays.asList("NORMAL", "10", "ALLOW");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 10);
        
        Assertions.assertEquals("ALLOW", action);
    }

    @Test
    void testCheckStatus_Block() {
        // Mock Redis return: ["BLOCKED", "110", "BLOCK"]
        List<String> mockResult = Arrays.asList("BLOCKED", "110", "BLOCK");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 100);
        
        Assertions.assertEquals("BLOCK", action);
    }

    @Test
    void testCheckStatus_Warning() {
        // Mock Redis return: ["OBSERVATION", "50", "WARNING"]
        List<String> mockResult = Arrays.asList("OBSERVATION", "50", "WARNING");
        
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(mockResult);

        String action = riskStateMachine.checkStatus("user1", 50);
        
        Assertions.assertEquals("WARNING", action);
    }
}
