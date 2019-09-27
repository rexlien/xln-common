package xln.common.serializer;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

@Slf4j
public class ProtoKafkaDeserializer<T extends Any> implements Deserializer<T> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public T deserialize(String topic, byte[] data) {

        try {
            return (T)Any.parseFrom(data);
        }catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void close() {

    }
}
