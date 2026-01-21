package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskProfileIntegrationTest {

    @Autowired
    private RiskStateMachine riskStateMachine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private static final String TEST_USER = "integration_test_user";

    @BeforeEach
    void setUp() {
        // Clear DB
        jdbcTemplate.update("DELETE FROM sys_user_risk_profile WHERE app_user_id = ?", TEST_USER);
    }

    @Test
    @Order(1)
    void testRiskAccumulationAndPersistence() throws InterruptedException {
        System.out.println(">>> Starting Integration Test: Risk Accumulation & Persistence");

        // Mock Redis behavior for "NORMAL" state
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(Arrays.asList("NORMAL", "10", "ALLOW"));

        // 1. Initial State: Normal (Score 10)
        String action1 = riskStateMachine.checkStatus(TEST_USER, 10);
        Assertions.assertEquals("ALLOW", action1);

        // Wait for async persistence
        Thread.sleep(1000);

        // Verify DB
        List<SysUserRiskProfile> profiles = jdbcTemplate.query(
                "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?",
                new BeanPropertyRowMapper<>(SysUserRiskProfile.class),
                TEST_USER
        );
        Assertions.assertFalse(profiles.isEmpty(), "Profile should be persisted");
        Assertions.assertEquals(10, profiles.get(0).getCurrentScore());
        Assertions.assertEquals("NORMAL", profiles.get(0).getRiskLevel());

        // Mock Redis behavior for "OBSERVATION" state
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(Arrays.asList("OBSERVATION", "50", "WARNING"));

        // 2. Trigger Warning (Score 50)
        String action2 = riskStateMachine.checkStatus(TEST_USER, 40);
        Assertions.assertEquals("WARNING", action2);

        Thread.sleep(1000);

        profiles = jdbcTemplate.query(
                "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?",
                new BeanPropertyRowMapper<>(SysUserRiskProfile.class),
                TEST_USER
        );
        Assertions.assertEquals(50, profiles.get(0).getCurrentScore());
        Assertions.assertEquals("OBSERVATION", profiles.get(0).getRiskLevel());

        // Mock Redis behavior for "BLOCKED" state
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(Arrays.asList("BLOCKED", "110", "BLOCK"));

        // 3. Trigger Block (Score 110)
        String action3 = riskStateMachine.checkStatus(TEST_USER, 60);
        Assertions.assertEquals("BLOCK", action3);

        Thread.sleep(1000);

        profiles = jdbcTemplate.query(
                "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?",
                new BeanPropertyRowMapper<>(SysUserRiskProfile.class),
                TEST_USER
        );
        Assertions.assertEquals(110, profiles.get(0).getCurrentScore());
        Assertions.assertEquals("BLOCKED", profiles.get(0).getRiskLevel());

        System.out.println(">>> Integration Test Passed: Risk Flow Confirmed");
    }
}