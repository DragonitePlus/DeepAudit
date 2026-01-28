package edu.hnu.deepaudit.proxy.factory;

import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;

/**
 * Factory to create AuditPersistenceService in non-Spring environment.
 */
public class AuditPersistenceServiceFactory {

    private static volatile AuditPersistenceService instance;

    public static AuditPersistenceService getInstance() {
        if (instance == null) {
            synchronized (AuditPersistenceServiceFactory.class) {
                if (instance == null) {
                    instance = createInstance();
                }
            }
        }
        return instance;
    }

    private static AuditPersistenceService createInstance() {
        // Initialize DataSource
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/deepaudit_sys?serverTimezone=Asia/Shanghai&characterEncoding=utf8");
        config.setUsername("root");
        config.setPassword("1740084968");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        DataSource dataSource = new HikariDataSource(config);

        // Initialize MyBatis
        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper.class);
        
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

        // Create Service
        AuditPersistenceService service = new AuditPersistenceService();
        service.setSysAuditLogMapper(sqlSessionFactory.openSession(true).getMapper(edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper.class));
        
        return service;
    }
}
