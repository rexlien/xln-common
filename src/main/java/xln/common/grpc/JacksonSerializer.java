package xln.common.grpc;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.protobuf.Message;
import xln.common.utils.ProtoUtils;

import java.io.IOException;

public class JacksonSerializer extends JsonSerializer<Message> {

    @Override
    public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(ProtoUtils.jsonUsingType(value));
    }
}

