package xln.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import xln.common.utils.ProtoUtils;

import java.io.IOException;

@Slf4j
public class JacksonProtoMessageDeserializer<T extends Message> extends JsonDeserializer<Message> {
    public JacksonProtoMessageDeserializer() {

    }

    @Override
    public Message deserialize(JsonParser parser, DeserializationContext deserializer) throws IOException {

        ObjectCodec codec = parser.getCodec();
        try {

            JsonNode node = codec.readTree(parser);
            var className = node.get("java_class").asText();

            return ProtoUtils.fromJson(node.toString(), className);
        }catch (Exception ex) {
            log.error("", ex);
            throw new IOException();
        }
    }

    @Override
    public Message deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {

        var typeProperty = typeDeserializer.getPropertyName();
        ObjectCodec codec = p.getCodec();

        try {

            JsonNode node = codec.readTree(p);
            var className = node.get(typeProperty).asText();

            return ProtoUtils.fromJson(node.toString(), className);
        }catch (Exception ex) {
            log.error("", ex);
        }
        return null;
    }


}
