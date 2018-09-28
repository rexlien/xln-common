package xln.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix="xln.service-config")
@Validated
@Component("XLNServiceConfig")
@Data
public class ServiceConfig
{

    private final RedisConfig redisConfig = new RedisConfig();
    private List<String> resourcePath;

    @Data
    public static class RedisConfig
    {
        private List<String> URI;

    }

}
