package edu.hnu.deepaudit.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@SpringBootTest
public class CleanupOldDataTest {

    @Autowired
    @Qualifier("systemDataSource")
    private DataSource systemDataSource;

    @Test
    public void dropOldTables() {
        System.out.println(">>> 开始清理源数据库 (deepaudit_sys) 中的旧表...");
        
        JdbcTemplate sysJdbc = new JdbcTemplate(systemDataSource);

        try {
            // 检查表是否存在（这里直接尝试删除，忽略不存在错误是简单的做法）
            // 在实际生产中应该先检查
            System.out.println("正在删除表 sys_user (如果存在)...");
            sysJdbc.execute("DROP TABLE IF EXISTS sys_user");
            System.out.println("表 sys_user 已删除或不存在。");
            
        } catch (Exception e) {
            System.err.println("清理失败: " + e.getMessage());
        }

        System.out.println(">>> 清理完成!");
    }
}
