package xln.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.validation.annotation.Validated;
import xln.common.service.MongoService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix="xln.mongo-config")
@Configuration
@Data
public class MongoConfig {

    @Data
    public static class MongoServerConfig {
        private String hosts = "127.0.0.1:27017";
        private String user = "root";
        private String pw = "1234";
        private String replSetName = null;

        private String database = "test";

        //millis
        private int connectionTimeout = 10 * 1000;
        private int minHostConnection = 5;

        private String writeConcern = null;
        private int writeAckTimeout = 15000;

        private boolean reactive = false;
        private boolean nonReactive = true;

    }

    private String autoConfigSpringTemplate = "default";

    private Map<String, MongoServerConfig> mongoConfigs = new HashMap<>();


    @ConditionalOnProperty(prefix ="xln.mongo-config", name = "autoConfigSpringTemplate")
    @Bean
    public MongoTemplate mongoTemplate(@Autowired MongoService mongoService) {

        return mongoService.getSpringTemplate(MongoTemplate.class);
    }


    @ConditionalOnProperty(prefix ="xln.mongo-config", name = "autoConfigSpringTemplate")
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(@Autowired MongoService mongoService) {
        return mongoService.getSpringTemplate(ReactiveMongoTemplate.class);
    }
}
