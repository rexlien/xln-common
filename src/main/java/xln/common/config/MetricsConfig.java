package xln.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {


    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(@Autowired CommonConfig commonConfig) {
        return registry -> registry.config().commonTags("application", commonConfig.getAppName());
    }
}
