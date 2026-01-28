//package edu.hnu.deepaudit.control;
//
//import edu.hnu.deepaudit.config.RiskProperties;
//import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
//import edu.hnu.deepaudit.model.SysUserRiskProfile;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.script.DefaultRedisScript;
//
//import java.util.Arrays;
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//import static org.mockito.internal.verification.VerificationModeFactory.times;
//
///**
// * 风险画像集成测试 (模拟)
// * 测试思想：
// * 1. 由于 Core 已去 Spring 化，我们不再进行真实的集成测试（依赖 Spring 容器和真实 DB）。
// * 2. 转而测试 RiskStateMachine 与 Persistence Service 的交互逻辑。
// * 3. 验证当 Redis 返回状态变更时，是否正确调用了 Mapper 进行持久化。
// */
//@ExtendWith(MockitoExtension.class)
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//class RiskProfileIntegrationTest {
//
//    @Mock
//    private RiskStateMachine riskStateMachine;
//
//    @Mock
//    private SysUserRiskProfileMapper sysUserRiskProfileMapper;
//
//    @Mock
//    private StringRedisTemplate redisTemplate;
//
//    @Mock
//    private RiskProperties riskProperties;
//
//    private static final String TEST_USER = "integration_test_user";
//
//    @BeforeEach
//    void setUp() {
//        // 使用真实对象进行部分逻辑测试，或者完全 Mock 交互
//        // 这里我们选择测试 "状态机" 自身逻辑，但 Mock 它的依赖
//        riskStateMachine = new RiskStateMachine();
//        riskStateMachine.setRedisTemplate(redisTemplate);
//        riskStateMachine.setRiskProperties(riskProperties);
//        riskStateMachine.setSysUserRiskProfileMapper(sysUserRiskProfileMapper);
//        riskStateMachine.init();
//
//        when(riskProperties.getDecayRate()).thenReturn(0.5);
//    }
//
//    @Test
//    @Order(1)
//    void testRiskAccumulationAndPersistence() {
//        System.out.println(">>> Starting Test: Risk Accumulation & Persistence Logic");
//
//        // Mock Redis 正常返回
//        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
//                .thenReturn(Arrays.asList("NORMAL", "10", "ALLOW"));
//
//        // 1. 测试正常流程
//        String action1 = riskStateMachine.checkStatus(TEST_USER, 10);
//        Assertions.assertEquals("ALLOW", action1);
//
//        // 验证 Mapper 被调用 (持久化)
//        // 注意：由于 persistRiskProfile 是异步执行，单元测试中可能需要一点等待或使用同步方式重构
//        // 为了测试稳定性，我们这里假设 persistRiskProfile 能被 verify 到
//        // 在真实环境中，CompletableFuture.runAsync 可能会在测试结束前还没跑完
//        // 但 Mockito verify 通常对 mock 对象调用是敏感的。
//        // 如果失败，可能需要 Thread.sleep(100)
//        try { Thread.sleep(100); } catch (InterruptedException e) {}
//        verify(sysUserRiskProfileMapper, times(1)).selectById(TEST_USER);
//        // 如果 selectById 返回 null，会调用 insert。这里我们 mock 默认返回 null
//        verify(sysUserRiskProfileMapper, times(1)).insert(any(SysUserRiskProfile.class));
//
//
//        // 2. 测试阻断流程
//        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
//                .thenReturn(Arrays.asList("BLOCKED", "110", "BLOCK"));
//
//        String action3 = riskStateMachine.checkStatus(TEST_USER, 100);
//        Assertions.assertEquals("BLOCK", action3);
//
//        try { Thread.sleep(100); } catch (InterruptedException e) {}
//        // 再次验证
//        verify(sysUserRiskProfileMapper, times(2)).selectById(TEST_USER);
//    }
//
//    @Test
//    @Order(2)
//    @Disabled("Functionality moved to Plugin Hook, no longer testable via Service call")
//    void testDatabaseRefusal() {
//        // 原测试思想：测试拦截器是否工作。
//        // 现况：拦截器逻辑已移动到 ShardingSphere Plugin Hook，核心库中不再包含拦截器。
//        // 因此标记为禁用。
//    }
//}