package xln.common.config;

import lombok.val;
import org.apache.spark.SparkConf;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
@ConfigurationProperties(prefix="xln.common.spark")
@ConditionalOnProperty(prefix ="xln.common.spark", name = "masterUrl")
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

    public String getExecutorJVMOptions() {
        return executorJVMOptions;
    }

    public SparkConfig setExecutorJVMOptions(String executorJVMOptions) {
        this.executorJVMOptions = executorJVMOptions;
        return this;
    }

    private String executorJVMOptions = "";
    //private int executorCount = 2;

    private String masterUrl = "";
    private boolean executorDebug = false;

    public String getTimeout() {
        return timeout;
    }

    public SparkConfig setTimeout(String timeout) {
        this.timeout = timeout;
        return this;
    }

    public String getDriverPort() {
        return driverPort;
    }

    public SparkConfig setDriverPort(String driverPort) {
        this.driverPort = driverPort;
        return this;
    }

    private String timeout = "600s";
    private String driverPort = "50999";

    public static SparkConf build(SparkConfig config) {

        var ip = "";
        try {
            var address = InetAddress.getLocalHost();
            ip = address.getHostAddress();
        } catch(Exception ex) {

        }

        var conf = new SparkConf().set("spark.driver.port", config.getDriverPort()).set("spark.driver.host", ip).set("spark.network.timeout", config.getTimeout()) //set("spark.executor.userClassPathFirst", "true").
                .setMaster(config.getMasterUrl());
        String jvmOption = "";
        if(!config.executorJVMOptions.isEmpty()) {
            jvmOption += config.executorJVMOptions;
        }
        jvmOption += " -Dlogging.pattern.console='%d %-5level [%thread] %logger : %msg%n'";
        if(config.isKubernetes()) {
            conf.set("spark.kubernetes.container.image", config.getExecutorImage());
        }
        if(config.isExecutorDebug()) {
            jvmOption += " -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005";
        }

        conf.set("spark.executor.extraJavaOptions", jvmOption);

        return conf;
    }




}
