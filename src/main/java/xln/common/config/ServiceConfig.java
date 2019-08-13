package xln.common.config;

import lombok.Data;
import org.omg.CosNaming.NamingContextExtPackage.StringNameHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import java.util.*;

@ConfigurationProperties(prefix="xln.service-config")
@Configuration("XLNServiceConfig")
@Data
public class ServiceConfig
{

    private List<String> resourcePath = Collections.EMPTY_LIST;


    private RedisConfig redisConfig = new RedisConfig();


    @Data
    public static class RedisConfig
    {
        private Map<String, RedisServerConfig> redisServerConfigs = Collections.EMPTY_MAP;
        private List<RedisScript> script = Collections.EMPTY_LIST;
    }

    @Data
    public static class RedisServerConfig
    {
        public enum RedisType {
            SINGLE("single"),
            SENTINEL("sentinel"),
            CLUSTER("cluster");

            private String type;

            RedisType(String type) {
                this.type = type;
            }

        };
        private RedisType type;
        private String password;
        private List<String> URI;
        private boolean slaveRead = false;
        private boolean useRedisson = false;

        private boolean publisher = false;
        private boolean subscriber = false;
    }

    @Data
    public static class RedisScript
    {
        private String name;
        private String path;
    }

 
}
