package xln.common.utils;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;

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
            return JsonFormat.printer().print(msg);
        }catch (Exception ex) {
            log.error("Print json failed", ex);
            return null;
        }
    }
}
