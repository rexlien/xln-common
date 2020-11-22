package xln.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import xln.common.annotation.ProxyEndpoint;
import xln.common.aspect.PropertyChangeAware;
import xln.common.proxy.EndPoint;

import java.util.ArrayList;

@ConfigurationProperties(prefix="xln.etcd-config")
@Configuration
public class EtcdConfig implements PropertyChangeAware {

    public EndPoint getEndPoint() {
        return endPoint;
    }

    public EtcdConfig setEndPoint(EndPoint endPoint) {

        this.endPoint = endPoint;
        return this;
    }

    public String getConfigNamespace() {
        return configNamespace;
    }

    public EtcdConfig setConfigNamespace(String configNamespace) {
        this.configNamespace = configNamespace;
        return this;
    }

    //@ProxyEndpoint
    private volatile EndPoint endPoint = new EndPoint();

    private volatile String configNamespace = "ns";

    public ArrayList<String> getConfigWatchDirs() {
        return configWatchDirs;
    }

    public EtcdConfig setConfigWatchDirs(ArrayList<String> configWatchDirs) {
        this.configWatchDirs = configWatchDirs;
        return this;
    }

    private volatile ArrayList<String> configWatchDirs = new ArrayList<>();

    public boolean isEnableVersionMeter() {
        return enableVersionMeter;
    }

    public EtcdConfig setEnableVersionMeter(boolean enableVersionMeter) {
        this.enableVersionMeter = enableVersionMeter;
        return this;
    }

    private volatile boolean enableVersionMeter = false;


}
