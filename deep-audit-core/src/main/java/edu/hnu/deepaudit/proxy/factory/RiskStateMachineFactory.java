package edu.hnu.deepaudit.proxy.factory;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;

/**
 * Factory to create RiskStateMachine instance in non-Spring environment (ShardingSphere Proxy).
 */
public class RiskStateMachineFactory {

    private static volatile RiskStateMachine instance;

    public static RiskStateMachine getInstance() {
        if (instance == null) {
            synchronized (RiskStateMachineFactory.class) {
                if (instance == null) {
                    instance = createInstance();
                }
            }
        }
        return instance;
    }

    private static RiskStateMachine createInstance() {
        // 1. Initialize Redis Template
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        JedisConnectionFactory connectionFactory = new JedisConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // 2. Initialize MyBatis Mapper for SysUserRiskProfile
        // Re-use logic from AuditPersistenceServiceFactory or duplicate simple setup
        // Ideally, share the DataSource, but for simplicity, create new for now or share via singleton
        SysUserRiskProfileMapper mapper = createMapper();

        // 3. Initialize RiskProperties
        RiskProperties riskProperties = new RiskProperties();
        riskProperties.setDecayRate(0.1);
        riskProperties.setObservationThreshold(60);
        riskProperties.setBlockThreshold(90);
        riskProperties.setWindowTtl(3600);
        riskProperties.setMlWeight(0.5);

        // 4. Create and Inject
        RiskStateMachine stateMachine = new RiskStateMachine();
        stateMachine.setRedisTemplate(redisTemplate);
        stateMachine.setRiskProperties(riskProperties);
        stateMachine.setSysUserRiskProfileMapper(mapper);
        stateMachine.init(); // Important: Load Lua script
        
        return stateMachine;
    }

    private static SysUserRiskProfileMapper createMapper() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/deepaudit_sys?serverTimezone=Asia/Shanghai&characterEncoding=utf8");
        config.setUsername("root");
        config.setPassword("1740084968");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        DataSource dataSource = new HikariDataSource(config);

        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(SysUserRiskProfileMapper.class);
        
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        return sqlSessionFactory.openSession(true).getMapper(SysUserRiskProfileMapper.class);
    }
}
