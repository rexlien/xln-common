package xln.common.utils;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

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
}
