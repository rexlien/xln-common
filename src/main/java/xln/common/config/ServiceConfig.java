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
@Validated
@Configuration("XLNServiceConfig")
@Data
public class ServiceConfig
{
    private RedisConfig redisConfig = new RedisConfig();
    private KafkaProducerConfig kafkaProducerConfig = new KafkaProducerConfig();
    private List<String> resourcePath;


    private RedisConfig2 redisConfig2 = new RedisConfig2();
    private KafkaConfig kafkaConfig = new KafkaConfig();

    @Data
    public static class KafkaConfig
    {
        private Map<String, KafkaConsumerConfig> consumersConifgs = Collections.EMPTY_MAP;
    }


    @Data
    public static class RedisConfig
    {
        private String password;
        private List<String> URI = Collections.singletonList("redis://127.0.0.1:6379");
        private boolean slaveRead = false;
        private List<RedisScript> script = Collections.EMPTY_LIST;

    }

    @Data
    public static class RedisConfig2
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
    }

    @Data
    public static class KafkaProducerConfig
    {
        private List<String> serverUrls = Collections.EMPTY_LIST;
        private String acks;
        private int requestTimeout;

    }

    @Data
    public static class KafkaConsumerConfig
    {
        private List<String> serverUrls = Collections.EMPTY_LIST;
        private String groupID;
        private String keyDeserializer = "org.apache.kafka.common.serialization.IntegerDeserializer";
        private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        private boolean enableAutoCommit = false;
        private int autoCommitInterval = 1000;
        private String autoOffsetResetConfig = "none";

    }

    @Data
    public static class RedisScript
    {
        private String name;
        private String path;
    }

 
}
