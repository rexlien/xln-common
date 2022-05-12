package xln.common.etcd

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "xln.dtask-config")
@Configuration
open class DTaskConfig {
/*
    open class DSchedulerConfig {

        val serviceGroup = ""

        val serviceName = ""

        //scheduler heartbeat interval in millis
        val scheduleInterval = 60000

        //if not null, relay task handling to external message broker
        val messageTopic: String? = null
    }
*/
    data class DSchedulerConfig(

        @Volatile var serviceGroup : String = "",

        @Volatile var serviceName : String = "",

        //scheduler heartbeat interval in millis
        @Volatile var scheduleInterval : Long = 60000,

        //if not null, relay task handling to external message broker
        @Volatile var messageTopic: String? = null
    )

    @Volatile var root = "xln-dtask"

    @Volatile var schedulerConfigs : List<DSchedulerConfig> = mutableListOf()

}

