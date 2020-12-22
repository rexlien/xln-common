package xln.common.serializer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

//string to Gson converted object, strip type information
@Slf4j
public class ObjectDeserializer extends JsonDeserializer<Object> {

    public ObjectDeserializer() {

    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext deserializer) {

        ObjectCodec codec = parser.getCodec();

        try {
            Gson gson = new Gson();
            JsonNode node = codec.readTree(parser);

            String text = "";
            if(node.isObject()) {

                text = node.toString();
            }
            else {
                text = node.asText();
                if (text == null) {
                    return "";
                }
            }

            try {
                var map = gson.fromJson(text, new TypeToken<Map<Object, Object>>() {}.getType());
                if(map != null) {
                    return map;
                }
                return text;
            }catch (JsonSyntaxException ex) {

                //if it's not json format return whole string
                log.info("json parse error, ignore and return as string: " + text);
                return text;
            }

        } catch (Exception ex) {
            log.error("", ex);
            return "";
        }

    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
        return this.deserialize(p, ctxt);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer, Object intoValue) throws IOException {
        return this.deserialize(p, ctxt);
    }
}