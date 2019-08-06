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
        private List<String> serverUrls = Collections.EMPTY_LIST;
        private String acks = "1";
        private int requestTimeout = 30000;
        private int retryCount = 0;
        //private int batchMem =
        //private int lingerMs

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


    private Map<String, KafkaConsumerConfig> consumersConfigs = Collections.EMPTY_MAP;
    private Map<String, KafkaProducerConfig> producerConfigs = Collections.EMPTY_MAP;
}
