package edu.hnu.deepaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "deepaudit.risk")
public class RiskProperties {

    /**
     * Decay rate per second (points/sec)
     */
    private double decayRate = 0.5;

    /**
     * Observation threshold (Score >= this triggers Warning/Window)
     */
    private int observationThreshold = 40;

    /**
     * Block threshold (Score >= this triggers Block)
     */
    private int blockThreshold = 100;

    /**
     * Observation Window TTL in seconds
     */
    private int windowTtl = 300;

    /**
     * Machine Learning Score Weight (0.0 - 1.0)
     * Default: 0.3 (30% ML, 70% Rules)
     */
    private double mlWeight = 0.3;

    /**
     * ONNX Model Path
     */
    private String modelPath = "D:/Code/DeepAudit/models/deep_audit_iso_forest.onnx";

    public double getDecayRate() {
        return decayRate;
    }

    public void setDecayRate(double decayRate) {
        this.decayRate = decayRate;
    }

    public int getObservationThreshold() {
        return observationThreshold;
    }

    public void setObservationThreshold(int observationThreshold) {
        this.observationThreshold = observationThreshold;
    }

    public int getBlockThreshold() {
        return blockThreshold;
    }

    public void setBlockThreshold(int blockThreshold) {
        this.blockThreshold = blockThreshold;
    }

    public int getWindowTtl() {
        return windowTtl;
    }

    public void setWindowTtl(int windowTtl) {
        this.windowTtl = windowTtl;
    }

    public double getMlWeight() {
        return mlWeight;
    }

    public void setMlWeight(double mlWeight) {
        this.mlWeight = mlWeight;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }
}
