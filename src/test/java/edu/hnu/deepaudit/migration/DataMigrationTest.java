package edu.hnu.deepaudit.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class DataMigrationTest {

    @Autowired
    @Qualifier("systemDataSource")
    private DataSource systemDataSource;

    @Autowired
    @Qualifier("businessDataSource")
    private DataSource businessDataSource;

    @Test
    public void migrateUserData() {
        System.out.println(">>> 开始数据迁移: deepaudit_sys -> deepaudit_biz");
        
        JdbcTemplate sysJdbc = new JdbcTemplate(systemDataSource);
        JdbcTemplate bizJdbc = new JdbcTemplate(businessDataSource);

        // 1. 确保目标库表存在
        System.out.println("[1/3] 检查目标库表结构...");
        bizJdbc.execute("CREATE TABLE IF NOT EXISTS sys_user (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) UNIQUE, " +
                "email VARCHAR(100) UNIQUE, " +
                "phone VARCHAR(20) UNIQUE" +
                ")");

        // 2. 从源库读取数据
        System.out.println("[2/3] 从源数据库 (deepaudit_sys) 读取 sys_user 数据...");
        List<Map<String, Object>> users;
        try {
             users = sysJdbc.queryForList("SELECT * FROM sys_user");
        } catch (Exception e) {
            System.out.println("源库中不存在 sys_user 表或数据为空，跳过迁移。");
            return;
        }

        if (users.isEmpty()) {
            System.out.println("没有需要迁移的用户数据。");
            return;
        }
        System.out.println("发现 " + users.size() + " 条用户记录。");

        // 3. 写入目标库
        System.out.println("[3/3] 写入目标数据库 (deepaudit_biz)...");
        int count = 0;
        for (Map<String, Object> user : users) {
            try {
                bizJdbc.update("INSERT INTO sys_user (id, username, email, phone) VALUES (?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE username=VALUES(username), email=VALUES(email), phone=VALUES(phone)",
                        user.get("id"), user.get("username"), user.get("email"), user.get("phone"));
                count++;
            } catch (Exception e) {
                System.err.println("迁移失败 ID=" + user.get("id") + ": " + e.getMessage());
            }
        }

        System.out.println(">>> 迁移完成! 成功迁移: " + count + "/" + users.size());
    }
}
