package xln.common.web.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.Message
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import xln.common.grpc.JacksonSerializer

@Configuration
open class Config {

    @Bean
    @Primary
    open fun configureObjectMapper(): ObjectMapper {

        //default handle serialize grpc message when output http
        val objectMapper = ObjectMapper()
        val myModule = SimpleModule("XLN-Module")

        myModule.addSerializer(Message::class.java, JacksonSerializer())
        objectMapper.registerModule(myModule)
        objectMapper.registerModule(KotlinModule())

        return objectMapper
    }
}