package xln.common.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import com.google.protobuf.util.JsonFormat
import mu.KotlinLogging
import xln.common.utils.ProtoUtils
import java.io.IOException

private val log = KotlinLogging.logger {}

class JacksonProtoAnySerializer(private val typeRegistry: JsonFormat.TypeRegistry) : JsonSerializer<com.google.protobuf.Any>() {

    @Throws(IOException::class)
    override fun serialize(value: com.google.protobuf.Any, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeRawValue(ProtoUtils.jsonUsingType(value, typeRegistry))
    }

    @Throws(IOException::class)
    override fun serializeWithType(value: com.google.protobuf.Any, gen: JsonGenerator, serializers: SerializerProvider, typeSer: TypeSerializer) {
        serialize(value, gen, serializers)
    }
}

class JacksonProtoAnyDeserializer(private val typeRegistry: JsonFormat.TypeRegistry): JsonDeserializer<com.google.protobuf.Any>() {
    @Throws(IOException::class)
    override fun deserialize(parser: JsonParser, deserializer: DeserializationContext): com.google.protobuf.Any {
        val codec = parser.codec
        return try {
            val node = codec.readTree<JsonNode>(parser)
            ProtoUtils.fromJson(node.toString(), com.google.protobuf.Any::class.java, typeRegistry)
        } catch (ex: Exception) {

            log.error("", ex)
            throw IOException()
        }

    }

    @Throws(IOException::class)
    override fun deserializeWithType(p: JsonParser, ctxt: DeserializationContext, typeDeserializer: TypeDeserializer): com.google.protobuf.Any  {

        return deserialize(p, ctxt)
    }
}