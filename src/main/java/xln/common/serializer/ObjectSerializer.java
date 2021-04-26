package xln.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.google.gson.Gson;

import java.io.IOException;

public class ObjectSerializer extends JsonSerializer<Object> {


    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

        var gson = new Gson();

        if (value instanceof String) {

            if (((String) value).isEmpty()){
                gen.writeString(value.toString());
            } else {
                gen.writeString(value.toString());//gson.toJson(value));
            }

        } else {
            gen.writeObject(value);
        }

    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
        this.serialize(value, gen, serializers);
    }


}
