package xln.common.serializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

public class Utils {

    //create resolver that skip container type, which might break json.
    public static ObjectMapper.DefaultTypeResolverBuilder createJsonCompliantResolverBuilder(ObjectMapper.DefaultTyping deaultTyping) {

        ObjectMapper.DefaultTypeResolverBuilder builder = new ObjectMapper.DefaultTypeResolverBuilder(deaultTyping) {
            {
                init(JsonTypeInfo.Id.CLASS, null);
                inclusion(JsonTypeInfo.As.PROPERTY);
                typeProperty("java_class");
            }

            @Override
            public boolean useForType(JavaType t) {
                return !t.isContainerType() && super.useForType(t);
            }
        };
        return builder;
    }

    public static ObjectMapper createObjectMapper() {

        var objectMapper = new ObjectMapper();
        objectMapper.registerModule(new KotlinModule());
        objectMapper.setDefaultTyping(Utils.createJsonCompliantResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }
}
