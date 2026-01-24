package edu.hnu.deepaudit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisConnection() {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();

        // 设置一个测试值
        String key = "test_connection";
        String value = "Hello Redis!";

        ops.set(key, value, 60, TimeUnit.SECONDS);

        // 获取测试值
        String result = ops.get(key);

        System.out.println("Redis连接测试结果: " + result);

        // 验证连接成功
        assert result != null && result.equals(value) : "Redis连接失败";

        // 删除测试数据
        stringRedisTemplate.delete(key);

        System.out.println("Redis连接测试通过!");
    }
}
