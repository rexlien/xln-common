package xln.common.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.redisson.misc.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.spi.Producer;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class KafkaService
{
    private static Logger logger = LoggerFactory.getLogger(KafkaService.class);

    private ServiceConfig.KafkaProducerConfig producerConfig;
    private Map<String, ServiceConfig.KafkaConsumerConfig> consumerConfigs;

    private KafkaSender<Integer, Object> sender;

    private Map<String, Object> producerProp = new HashMap<>();

    private Map<String, Map<String, Object>> consumerProps = new HashMap<>();

    @Autowired
    private ServiceConfig serviceConfig;

    public KafkaService() {

    }

    @PostConstruct
    private void init() {
        producerConfig = serviceConfig.getKafkaProducerConfig();
        if(producerConfig != null) {

            StringBuilder builder = new StringBuilder();
            boolean firstIter = true;
            for(String url : producerConfig.getServerUrls()) {

                if(firstIter) {
                    firstIter = false;
                } else {
                    builder.append(",");
                }
                builder.append(url);
            }
            producerProp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, builder.toString());
            try {
                producerProp.put(ProducerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().getHostName());
            }catch(UnknownHostException ex) {
                logger.error(ex.toString());
            }
            producerProp.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producerConfig.getRequestTimeout());
            producerProp.put(ProducerConfig.ACKS_CONFIG, producerConfig.getAcks());


        }

        consumerConfigs = serviceConfig.getKafkaConfig().getConsumersConifgs();
        for(Map.Entry<String, ServiceConfig.KafkaConsumerConfig> kv : consumerConfigs.entrySet()) {

            StringBuilder builder = new StringBuilder();
            boolean firstIter = true;
            for(String url : kv.getValue().getServerUrls()) {

                if(firstIter) {
                    firstIter = false;
                } else {
                    builder.append(",");
                }
                builder.append(url);
            }

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, builder.toString());
            try {
                props.put(ConsumerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().getHostName());
            }catch (UnknownHostException ex) {
                logger.error(ex.toString());
            }
            props.put(ConsumerConfig.GROUP_ID_CONFIG, kv.getValue().getGroupID());
            try {
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, Class.forName(kv.getValue().getKeyDeserializer()));
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Class.forName(kv.getValue().getValueDeserializer()));
            }catch (ClassNotFoundException ex) {
                logger.error(ex.toString());
            }
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kv.getValue().getAutoOffsetResetConfig());
            consumerProps.put(kv.getKey(), props);
        }
    }

    public <K, V, T, U> KafkaSender<K, V> createProducer(Class<T> kClass, Class<U> vClass) {

        HashMap<String, Object> newMap = new HashMap<String, Object>(producerProp);
        newMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kClass);
        newMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, vClass);
        SenderOptions<K, V> senderOptions = SenderOptions.create(newMap);

        return KafkaSender.create(senderOptions);

    };

    public <K, V> Flux<ReceiverRecord<K, V>> startConsume(String consumerName, Collection<String> topic) {

        ReceiverOptions<K, V> receiverOption =  ReceiverOptions.create(consumerProps.get(consumerName));
        receiverOption.subscription(topic);
        return KafkaReceiver.create(receiverOption).receive();

    }



}
