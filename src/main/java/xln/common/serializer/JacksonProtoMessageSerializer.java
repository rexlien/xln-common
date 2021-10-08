package xln.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.google.gson.Gson;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import org.apache.kafka.common.serialization.Serializer;
import xln.common.utils.ProtoUtils;

import java.io.IOException;
import java.util.Map;

import static com.fasterxml.jackson.core.JsonToken.START_OBJECT;

public class JacksonProtoMessageSerializer<T extends Message> extends JsonSerializer<Message> {


    @Override
    public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

        gen.writeRawValue(ProtoUtils.json(value));


    }

    @Override
    public void serializeWithType(Message value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {

        var typeId = typeSer.typeId(value, START_OBJECT);
        typeSer.writeTypePrefix(gen, typeId);

        var protoStr = ProtoUtils.json(value);
        //to merge with type field properly, strip curly bracket
        protoStr = protoStr.substring(1, protoStr.length() - 1);
        gen.writeRaw(",");
        gen.writeRaw(protoStr);
        typeSer.writeTypeSuffix(gen, typeId);

    }




}