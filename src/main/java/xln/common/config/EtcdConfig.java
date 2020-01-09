package xln.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix="xln.etcd-config")
@Configuration
@Data
public class EtcdConfig {

    private volatile String hosts;

}
