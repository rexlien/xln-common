package xln.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import xln.common.utils.ProtoUtils;

import java.io.IOException;

import static com.fasterxml.jackson.core.JsonToken.START_OBJECT;

public class JacksonProtoMessageSerializer extends JsonSerializer<Message> {

    private JsonFormat.TypeRegistry typeRegistry = null;
    public JacksonProtoMessageSerializer() {

    }

    public JacksonProtoMessageSerializer(JsonFormat.TypeRegistry typeRegistry) {

        this.typeRegistry = typeRegistry;
    }


    @Override
    public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

        if(typeRegistry != null) {
            gen.writeRawValue(ProtoUtils.jsonUsingType(value, typeRegistry));
        } else {
            gen.writeRawValue(ProtoUtils.json(value));
        }
    }

    @Override
    public void serializeWithType(Message value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {

        var typeId = typeSer.typeId(value, START_OBJECT);
        typeSer.writeTypePrefix(gen, typeId);

        var protoStr = "";
        if(typeRegistry != null) {
            protoStr = ProtoUtils.jsonUsingType(value, typeRegistry);
        } else {
            protoStr = ProtoUtils.json(value);
        }

        //to merge with type field properly, strip curly bracket
        protoStr = protoStr.substring(1, protoStr.length() - 1);
        gen.writeRaw(",");
        gen.writeRaw(protoStr);
        typeSer.writeTypeSuffix(gen, typeId);

    }




}