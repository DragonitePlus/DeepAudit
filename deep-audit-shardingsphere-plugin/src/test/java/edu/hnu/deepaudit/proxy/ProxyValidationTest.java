package edu.hnu.deepaudit.proxy;

import edu.hnu.deepaudit.control.RiskStateMachine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 单元测试：验证 DeepAuditHook 对 SQL Hint 的解析和阻断逻辑
 * 适配 ShardingSphere 5.4.1 API
 */
public class ProxyValidationTest {

    @Test
    public void testBlockLogic() throws Exception {
        // 1. 准备待测试的 Hook 对象
        DeepAuditHook hook = new DeepAuditHook();

        // 2. 创建一个假的 RiskStateMachine (Mock)
        RiskStateMachine mockMachine = Mockito.mock(RiskStateMachine.class);

        // 3. 定义 Mock 的行为：当检查 "blocked_user" 时，返回 "BLOCK"
        when(mockMachine.checkStatus(eq("blocked_user"), anyInt())).thenReturn("BLOCK");

        // 4. 【关键步骤】利用反射，把 Hook 内部那个连真实 Redis 的 StateMachine 替换成我们的 Mock 对象
        // 这样测试运行时就不需要启动 Redis 了
        injectMockStateMachine(hook, mockMachine);

        // 5. 构造测试数据 (模拟 SQL Hint)
        String sql = "/* user_id:blocked_user */ SELECT * FROM sys_user";

        // 6. 执行测试：预期应该抛出 RuntimeException
        assertThrows(RuntimeException.class, () -> {
            // 调用 5.4.1 标准的 start 方法签名
            // 参数: dataSourceName, sql, parameters, connectionProperties, isTrunkThread
            hook.start("ds_0", sql, Collections.emptyList(), null, true);
        }, "Should throw exception for blocked user");
    }

    @Test
    public void testAllowLogic() throws Exception {
        // 1. 准备对象
        DeepAuditHook hook = new DeepAuditHook();
        RiskStateMachine mockMachine = Mockito.mock(RiskStateMachine.class);

        // 2. 定义 Mock 的行为：当检查 "normal_user" 时，返回 "ALLOW"
        when(mockMachine.checkStatus(eq("normal_user"), anyInt())).thenReturn("ALLOW");

        // 3. 注入 Mock
        injectMockStateMachine(hook, mockMachine);

        // 4. 构造测试数据
        String sql = "/* user_id:normal_user */ SELECT * FROM sys_user";

        // 5. 执行测试：预期不抛出任何异常
        assertDoesNotThrow(() -> {
            hook.start("ds_0", sql, Collections.emptyList(), null, true);
        });
    }

    @Test
    public void testNoHintLogic() throws Exception {
        DeepAuditHook hook = new DeepAuditHook();
        RiskStateMachine mockMachine = Mockito.mock(RiskStateMachine.class);

        // 如果没有 Hint，解析出来应该是 "unknown"
        when(mockMachine.checkStatus(eq("unknown"), anyInt())).thenReturn("ALLOW");

        injectMockStateMachine(hook, mockMachine);

        String sql = "SELECT * FROM sys_user"; // 没有 user_id 注释

        assertDoesNotThrow(() -> {
            hook.start("ds_0", sql, Collections.emptyList(), null, true);
        });
    }

    /**
     * 反射工具方法：强制替换私有字段
     */
    private void injectMockStateMachine(DeepAuditHook hook, RiskStateMachine mockMachine) throws Exception {
        // 获取 DeepAuditHook 类中的 riskStateMachine 字段
        Field field = DeepAuditHook.class.getDeclaredField("riskStateMachine");
        // 暴力破解私有权限
        field.setAccessible(true);
        // 将 mock 对象塞进去
        field.set(hook, mockMachine);
    }
}