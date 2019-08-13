package xln.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix="xln.kafka-config")
@Configuration
@Data
public class KafkaConfig {

    @Data
    public static class KafkaProducerConfig
    {
        private volatile List<String> serverUrls = Collections.EMPTY_LIST;
        private volatile String acks = "1";
        private volatile int requestTimeout = 30000;
        private volatile int retryCount = 0;
        //private int batchMem =
        //private int lingerMs

    }

    @Data
    public static class KafkaConsumerConfig
    {
        private volatile List<String> serverUrls = Collections.EMPTY_LIST;
        private volatile String groupID;
        private volatile String keyDeserializer = "org.apache.kafka.common.serialization.IntegerDeserializer";
        private volatile String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        private volatile boolean enableAutoCommit = false;
        private volatile int autoCommitInterval = 1000;
        private volatile String autoOffsetResetConfig = "none";

    }


    private volatile Map<String, KafkaConsumerConfig> consumersConfigs = Collections.EMPTY_MAP;
    private volatile Map<String, KafkaProducerConfig> producerConfigs = Collections.EMPTY_MAP;
}
