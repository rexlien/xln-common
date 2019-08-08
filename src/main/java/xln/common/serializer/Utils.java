package xln.common.serializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

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
}
