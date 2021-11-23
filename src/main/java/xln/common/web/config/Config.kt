package xln.common.web.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.Message
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import xln.common.grpc.ProtobufService
import xln.common.serializer.JacksonProtoAnySerializer
import xln.common.serializer.JacksonProtoMessageSerializer

@Configuration("XLNWebConfig")
open class Config {

    @Bean
    @Primary
    open fun configureObjectMapper(protobufService: ProtobufService): ObjectMapper {

        //default handle serialize grpc message when output http
        val objectMapper = ObjectMapper()
        val myModule = SimpleModule("XLN-Module")

        myModule.addSerializer(Message::class.java, JacksonProtoMessageSerializer<Message>())
        myModule.addSerializer(com.google.protobuf.Any::class.java, JacksonProtoAnySerializer(protobufService.getTypeRegistry()))
        objectMapper.registerModule(myModule)
        objectMapper.registerModule(KotlinModule())

        return objectMapper
    }
}