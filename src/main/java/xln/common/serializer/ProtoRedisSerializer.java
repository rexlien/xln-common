package xln.common.serializer;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class ProtoRedisSerializer implements RedisSerializer<Any> {

    @Override
    public byte[] serialize(Any m) throws SerializationException {
        return m.toByteArray();
    }

    @Override
    public Any deserialize(byte[] bytes) throws SerializationException {
        try {
            return Any.parseFrom(bytes);
        }catch(InvalidProtocolBufferException ex) {
            return null;
        }
        //return/// Parser<T>.
    }
}
