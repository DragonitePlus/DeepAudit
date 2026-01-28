package edu.hnu.deepaudit.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * DeepAudit Plugin Configuration
 * Loads properties from deepaudit.properties
 */
public class DeepAuditConfig {

    private final Properties properties = new Properties();

    public DeepAuditConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("deepaudit.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find deepaudit.properties");
                return;
            }
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public double getDoubleProperty(String key, double defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Datasource
    public String getJdbcUrl() { return getProperty("deepaudit.datasource.jdbc-url"); }
    public String getDbUsername() { return getProperty("deepaudit.datasource.username"); }
    public String getDbPassword() { return getProperty("deepaudit.datasource.password"); }
    public String getDbDriver() { return getProperty("deepaudit.datasource.driver-class-name"); }

    // Redis
    public String getRedisHost() { return getProperty("deepaudit.redis.host", "localhost"); }
    public int getRedisPort() { return getIntProperty("deepaudit.redis.port", 6379); }
    public String getRedisPassword() { return getProperty("deepaudit.redis.password", null); }
    public int getRedisTimeout() { return getIntProperty("deepaudit.redis.timeout", 2000); }

    // AI
    public String getAiServiceUrl() { return getProperty("deepaudit.ai.url", "http://localhost:5000/predict_risk"); }
    
    // Risk
    public double getDecayRate() { return getDoubleProperty("deepaudit.risk.decay-rate", 0.1); }
    public int getObservationThreshold() { return getIntProperty("deepaudit.risk.observation-threshold", 60); }
    public int getBlockThreshold() { return getIntProperty("deepaudit.risk.block-threshold", 90); }
    public int getWindowTtl() { return getIntProperty("deepaudit.risk.window-ttl", 3600); }
}
