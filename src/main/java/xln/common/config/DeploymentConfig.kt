package xln.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "xln.deployment-config")
@Configuration
open class DeploymentConfig {

    open class SpinnakerConfig {
        var url = ""
        var webhook = ""
    }
    @Volatile var enable = false
    @Volatile var spinnakerConfig  = SpinnakerConfig()

}