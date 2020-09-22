package xln.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix="xln.etcd-config")
@Configuration
public class EtcdConfig {

    public String getHosts() {
        return hosts;
    }

    public EtcdConfig setHosts(String hosts) {
        this.hosts = hosts;
        return this;
    }

    public String getConfigNamespace() {
        return configNamespace;
    }

    public EtcdConfig setConfigNamespace(String configNamespace) {
        this.configNamespace = configNamespace;
        return this;
    }

    private volatile String hosts;

    private volatile String configNamespace = "ns";

}
