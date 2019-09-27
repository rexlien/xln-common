package xln.common.serializer;

import com.google.protobuf.Any;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class ProtoKafkaSerializer<T extends Any> implements Serializer<T> {
    @Override
    public void configure(Map configs, boolean isKey) {

    }

    @Override
    public byte[] serialize(String topic, Any data) {
        return data.toByteArray();
    }

    @Override
    public void close() {

    }
}
