package xln.common.grpc


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat.TypeRegistry
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import xln.common.proto.api.Api
import xln.common.serializer.JacksonProtoAnyDeserializer
import xln.common.serializer.JacksonProtoAnySerializer
import xln.common.serializer.Utils
import xln.common.utils.ProtoUtils

interface ProtobufConfigAware {

    //should return all the protobuf type that used as Any
    fun getTypeDescriptor() : List<Descriptors.Descriptor>

}

@Component
class DefaultProtobufConfig : ProtobufConfigAware{
    override fun getTypeDescriptor(): List<Descriptors.Descriptor> {
        return mutableListOf(Api.HttpApi.getDescriptor())
    }
}


@Service
class ProtobufService(private val protobufConfigAwares: List<ProtobufConfigAware>) {

    private val protobufRegistry : TypeRegistry =
            run {
                val typeRegistryBuilder = TypeRegistry.newBuilder()
                protobufConfigAwares.forEach {
                    typeRegistryBuilder.add(it.getTypeDescriptor())
                }
                typeRegistryBuilder.build()
            }


    fun getTypeRegistry() : TypeRegistry {
        return protobufRegistry
    }

    fun createObjectMapper() : ObjectMapper {

        val objMapper = Utils.createObjectMapper()
        val module = SimpleModule()
        module.addSerializer(com.google.protobuf.Any::class.java, JacksonProtoAnySerializer(protobufRegistry))
        module.addDeserializer(com.google.protobuf.Any::class.java, JacksonProtoAnyDeserializer(protobufRegistry))
        objMapper.registerModule(module)
        return objMapper
    }

    fun toJson(message: Message) : String {
        return ProtoUtils.jsonUsingType(message, protobufRegistry)
    }

     fun <T : Message> fromJson(json: String, clazz: Class<T>) : T {
        return ProtoUtils.fromJson(json, clazz, protobufRegistry)
    }
}