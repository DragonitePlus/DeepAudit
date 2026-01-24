package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.exception.RiskControlException;
import edu.hnu.deepaudit.interception.UserContext;
import edu.hnu.deepaudit.mapper.biz.SysUserMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskProfileIntegrationTest {

    // Spy 的作用：默认执行真实逻辑（保证 Test 1 通过），但允许我们强制修改特定方法的返回值（保证 Test 2 通过）
    @MockitoSpyBean
    private RiskStateMachine riskStateMachine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private static final String TEST_USER = "integration_test_user";

    @BeforeEach
    void setUp() {
        System.out.println(">>> [SetUp] Cleaning up test data for " + TEST_USER);
        jdbcTemplate.update("DELETE FROM sys_user_risk_profile WHERE app_user_id = ?", TEST_USER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = 'TestUser'");
        jdbcTemplate.update("INSERT INTO sys_user (id, username) VALUES (999, 'TestUser') ON DUPLICATE KEY UPDATE username='TestUser'");
    }

    @AfterEach
    void tearDown() {
        System.out.println(">>> [TearDown] Cleaning up test data for " + TEST_USER);
        jdbcTemplate.update("DELETE FROM sys_user_risk_profile WHERE app_user_id = ?", TEST_USER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = 'TestUser'");
        System.out.println(">>> [TearDown] Cleanup complete.");
    }

    @Test
    @Order(1)
    void testRiskAccumulationAndPersistence() throws InterruptedException {
        System.out.println(">>> Starting Integration Test: Risk Accumulation & Persistence");

        // Spy 默认会走真实逻辑，所以我们需要 Mock Redis 让真实逻辑跑通
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(Arrays.asList("NORMAL", "10", "ALLOW"));

        // 1. 测试正常流程
        String action1 = riskStateMachine.checkStatus(TEST_USER, 10);
        Assertions.assertEquals("ALLOW", action1);

        Thread.sleep(1000);

        List<SysUserRiskProfile> profiles = jdbcTemplate.query(
                "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?",
                new BeanPropertyRowMapper<>(SysUserRiskProfile.class),
                TEST_USER
        );
        Assertions.assertFalse(profiles.isEmpty());
        Assertions.assertEquals(10, profiles.get(0).getCurrentScore());

        // 2. 测试阻断流程（状态机逻辑验证）
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(Arrays.asList("BLOCKED", "110", "BLOCK"));

        String action3 = riskStateMachine.checkStatus(TEST_USER, 100);
        Assertions.assertEquals("BLOCK", action3);

        // 验证数据库是否更新为 BLOCKED
        Thread.sleep(1000);
        profiles = jdbcTemplate.query(
                "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?",
                new BeanPropertyRowMapper<>(SysUserRiskProfile.class),
                TEST_USER
        );
        Assertions.assertEquals("BLOCKED", profiles.get(0).getRiskLevel());
    }

    @Test
    @Order(2)
    void testDatabaseRefusal() {
        System.out.println(">>> Starting Integration Test: Database Refusal");

        // 这里我们绕过了 Redis，直接命令状态机："不管发生什么，你现在就告诉我用户被 BLOCK 了"
        // 这样可以 100% 确保测试的是“拦截器是否工作”，而不是测试“状态机或 Redis Mock 是否正常”
        doReturn("BLOCK").when(riskStateMachine).checkStatus(anyString(), anyInt());

        UserContext.setUserId(TEST_USER);
        try {
            // 4. 验证拦截器抛出异常
            // MyBatis Plus 可能会将底层异常包装为 MyBatisSystemException
            Exception exception = Assertions.assertThrows(Exception.class, () -> {
                sysUserMapper.selectById(999L);
            }, "Should throw exception when accessing DB while blocked");
            
            // 验证异常链中是否包含 RiskControlException
            Throwable rootCause = exception;
            boolean found = false;
            while (rootCause != null) {
                if (rootCause instanceof RiskControlException) {
                    found = true;
                    break;
                }
                rootCause = rootCause.getCause();
            }
            Assertions.assertTrue(found, "Exception cause should be RiskControlException");

        } finally {
            UserContext.clear();
        }
        System.out.println(">>> Integration Test Passed: Database Refusal Confirmed");
    }
}