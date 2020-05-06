package xln.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="xln.common.spark")
public class SparkConfig {

    public String getMasterUrl() {
        return masterUrl;
    }

    public SparkConfig setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
        return this;
    }

    public boolean isExecutorDebug() {
        return executorDebug;
    }

    public SparkConfig setExecutorDebug(boolean executorDebug) {
        this.executorDebug = executorDebug;
        return this;
    }

    public String getExecutorImage() {
        return executorImage;
    }

    public SparkConfig setExecutorImage(String executorImage) {
        this.executorImage = executorImage;
        return this;
    }

    public boolean isKubernetes() {
        return kubernetes;
    }

    public SparkConfig setKubernetes(boolean kubernetes) {
        this.kubernetes = kubernetes;
        return this;
    }

    private boolean kubernetes = false;
    private String executorImage = "rlien/spark";

    private String masterUrl = "";
    private boolean executorDebug = false;



}
