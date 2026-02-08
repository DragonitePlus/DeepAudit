package edu.hnu.deepaudit.proxy.factory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.config.DeepAuditConfig;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.persistence.JdbcRepository;
import edu.hnu.deepaudit.service.AnomalyDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Main Factory for DeepAudit Plugin Components.
 * Handles initialization of Config, DataSource, Redis, and Services.
 * Singleton Pattern.
 */
public class DeepAuditFactory {
    
    private static final Logger log = LoggerFactory.getLogger(DeepAuditFactory.class);

    private static volatile DeepAuditFactory instance;

    private final DeepAuditConfig config;
    private final HikariDataSource dataSource;
    private final JedisPool jedisPool;
    private final JdbcRepository jdbcRepository;
    private final RiskStateMachine riskStateMachine;
    private final AnomalyDetectionService anomalyDetectionService;
    private DlpEngine dlpEngine;

    private DeepAuditFactory() {
        log.info("Initializing DeepAuditFactory...");

        // 1. Load Config
        this.config = new DeepAuditConfig();

        // 2. Init DataSource (HikariCP)
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getDbUsername());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setDriverClassName(config.getDbDriver());
        hikariConfig.setMaximumPoolSize(5); // Conservative pool size for plugin
        this.dataSource = new HikariDataSource(hikariConfig);

        // 3. Init Redis (JedisPool)
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        String redisPassword = config.getRedisPassword();
        if (redisPassword != null && redisPassword.isEmpty()) {
            redisPassword = null; // Jedis expects null for no password
        }
        this.jedisPool = new JedisPool(poolConfig, config.getRedisHost(), config.getRedisPort(), 
                config.getRedisTimeout(), redisPassword);

        // 4. Init Repository
        this.jdbcRepository = new JdbcRepository(dataSource);

        this.dlpEngine = new DlpEngine();
        try {
            this.dlpEngine.setSensitiveTables(this.jdbcRepository.getAllSensitiveTables());
        } catch (Exception e) {
            log.error("Failed to load initial sensitive tables", e);
        }

        this.anomalyDetectionService = new AnomalyDetectionService();
        this.anomalyDetectionService.setJedisPool(jedisPool);

        // 5. Init Risk State Machine & Callback
        this.riskStateMachine = new RiskStateMachine();
        this.riskStateMachine.setJedisPool(jedisPool);
        this.riskStateMachine.setConfig(config);
        this.riskStateMachine.setJdbcRepository(jdbcRepository);

        this.riskStateMachine.setOnConfigUpdate(() -> {
            log.info("Config update detected, reloading components...");
            this.anomalyDetectionService.reloadModel(config.getModelPath());
            this.dlpEngine.setSensitiveTables(this.jdbcRepository.getAllSensitiveTables());
        });

        this.riskStateMachine.init();
        this.anomalyDetectionService.reloadModel(config.getModelPath());

        log.info("DeepAuditFactory initialized successfully.");
    }

    public static DeepAuditFactory getInstance() {
        if (instance == null) {
            synchronized (DeepAuditFactory.class) {
                if (instance == null) {
                    instance = new DeepAuditFactory();
                }
            }
        }
        return instance;
    }

    public RiskStateMachine getRiskStateMachine() {
        return riskStateMachine;
    }

    public AnomalyDetectionService getAnomalyDetectionService() {
        return anomalyDetectionService;
    }

    public DlpEngine getDlpEngine() {
        return dlpEngine;
    }
    
    public JdbcRepository getJdbcRepository() {
        return jdbcRepository;
    }

    // Hook to close resources on shutdown if needed
    public void close() {
        if (jedisPool != null) jedisPool.close();
        if (dataSource != null) dataSource.close();
    }
}
