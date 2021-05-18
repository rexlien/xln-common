package xln.common.utils;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

@Slf4j
public final class ProtoUtils {

    public static <T extends Message> T checkUnpack(Any any, Class<T> clazz) {
        if(any.is(clazz)) {
           return unpack(any, clazz);
        }
        return null;
    }

    public static <T extends Message> T unpack(Any any, Class<T> clazz) {

        try {
            return any.unpack(clazz);
        } catch (InvalidProtocolBufferException ex) {
            return null;
        }

    }

    public static String json(Message msg) {
        try {
            return JsonFormat.printer().omittingInsignificantWhitespace().print(msg);
        }catch (Exception ex) {
            log.error("Print json failed", ex);
            return null;
        }
    }

    public static String jsonUsingType(Message msg) {

        try {
            var type = JsonFormat.TypeRegistry.newBuilder().add(msg.getDescriptorForType()).build();
            return JsonFormat.printer().omittingInsignificantWhitespace().usingTypeRegistry(type).print(msg);
        }catch (Exception ex) {
            log.error("Print json failed", ex);
            return null;
        }
    }

    public static <T extends Message> T fromJson(String json, Class<T> msgClazz, String typeClass)  {
        if(typeClass == null) {
            return fromJson(json, msgClazz);
        }
        Message.Builder builder;
        try {
            // Since we are dealing with a Message type, we can call newBuilder()
            builder = (Message.Builder) msgClazz.getMethod("newBuilder").invoke(null);
            Class typeClazz = Class.forName(typeClass);
            Message.Builder typeClassBuilder = (Message.Builder) typeClazz.getMethod("newBuilder").invoke(null);
            var registry = JsonFormat.TypeRegistry.newBuilder().add(typeClassBuilder.getDescriptorForType()).build();

            JsonFormat.parser().usingTypeRegistry(registry).ignoringUnknownFields().merge(json, builder);

        } catch (Exception ex) {
            log.error("", ex);
            return null;
        }
        // the instance will be from the build
        return (T) builder.build();
    }

    public static <T extends Message> T fromJson(String json, Class<T> clazz)  {
        Message.Builder builder = null;
        try {
            // Since we are dealing with a Message type, we can call newBuilder()
            builder = (Message.Builder) clazz.getMethod("newBuilder").invoke(null);
            var registry = JsonFormat.TypeRegistry.newBuilder().add(builder.getDescriptorForType()).build();

            JsonFormat.parser().usingTypeRegistry(registry).ignoringUnknownFields().merge(json, builder);

        } catch (Exception ex) {
            log.error("", ex);
            return null;
        }
        // the instance will be from the build
        return (T) builder.build();
    }

    public static <T extends Message> T fromJson(String json, String clazzName)  {

        Class<T> clazz;
        try {
            clazz = (Class<T>)Class.forName(clazzName);
        }catch (Exception ex) {
            log.error("", ex);
            return null;
        }
        return fromJson(json, clazz);


    }
}
