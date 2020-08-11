package xln.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "xln.cluster-config")
@Configuration
open class ClusterConfig {

    @Volatile var port = 47000
}