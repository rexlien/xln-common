package xln.common.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import xln.common.config.KafkaConfig;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class KafkaService {
    private static Logger logger = LoggerFactory.getLogger(KafkaService.class);

    private Map<String, KafkaConfig.KafkaProducerConfig> producerConfigs;
    private Map<String, KafkaConfig.KafkaConsumerConfig> consumerConfigs;

    //private KafkaSender<Integer, Object> sender;

    //private Map<String, Object> producerProp = new HashMap<>();

    private ConcurrentHashMap<String, Map<String, Object>> producerProps = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Map<String, Object>> consumerProps = new ConcurrentHashMap<>();


    private ConcurrentHashMap<String, KafkaSender<String, Object>> kafkaSenders = new ConcurrentHashMap<>();
    private AtomicInteger consumerID = new AtomicInteger(0);
    private AtomicInteger correlationID = new AtomicInteger(0);

    @Autowired
    private KafkaConfig kafkaConfig;

    public KafkaService() {

    }

    @PostConstruct
    private void init() {
        producerConfigs = kafkaConfig.getProducerConfigs();
        if (producerConfigs != null) {
            for (Map.Entry<String, KafkaConfig.KafkaProducerConfig> kv : producerConfigs.entrySet()) {

                StringBuilder builder = new StringBuilder();
                boolean firstIter = true;
                for (String url : kv.getValue().getServerUrls()) {

                    if (firstIter) {
                        firstIter = false;
                    } else {
                        builder.append(",");
                    }
                    builder.append(url);
                }

                Map<String, Object> props = new HashMap<>();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, builder.toString());

                props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, kv.getValue().getRequestTimeout());
                props.put(ProducerConfig.ACKS_CONFIG, kv.getValue().getAcks());
                props.put(ProducerConfig.RETRIES_CONFIG, kv.getValue().getRetryCount());


                producerProps.put(kv.getKey(), props);
            }
        }

        consumerConfigs = kafkaConfig.getConsumersConfigs();
        for (Map.Entry<String, KafkaConfig.KafkaConsumerConfig> kv : consumerConfigs.entrySet()) {

            StringBuilder builder = new StringBuilder();
            boolean firstIter = true;
            for (String url : kv.getValue().getServerUrls()) {

                if (firstIter) {
                    firstIter = false;
                } else {
                    builder.append(",");
                }
                builder.append(url);
            }

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, builder.toString());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, kv.getValue().getGroupID());

            try {
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, Class.forName(kv.getValue().getKeyDeserializer()));
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, Class.forName(kv.getValue().getValueDeserializer()));
                props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            } catch (ClassNotFoundException ex) {
                logger.error(ex.toString());
            }
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kv.getValue().getAutoOffsetResetConfig());
            if (kv.getValue().isEnableAutoCommit()) {
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kv.getValue().isEnableAutoCommit());
                props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, kv.getValue().getAutoCommitInterval());
            }
            consumerProps.put(kv.getKey(), props);

        }
    }

    public <K, V, T, U> KafkaSender<K, V> createProducer(String producerConfig, Class<T> kClass, Class<U> vClass) {

        if (producerProps.get(producerConfig) == null) {
            return null;
        }
        HashMap<String, Object> newMap = new HashMap<>(producerProps.get(producerConfig));

        newMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kClass);
        newMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, vClass);
        SenderOptions<K, V> senderOptions = SenderOptions.create(newMap);

        return KafkaSender.create(senderOptions);

    }

    ;

    public <T> KafkaSender<String, Object> createProducer(String name, String producerConfig, Class<T> valueSerializer) {

        KafkaSender<String, Object> sender = createProducer(producerConfig, StringSerializer.class, valueSerializer);
        if (sender != null) {
            kafkaSenders.put(name, sender);
        }
        return sender;

    }

    ;

    public KafkaSender<String, Object> getProducer(String name) {
        return kafkaSenders.get(name);
    }

    public <K, V> Flux<ReceiverRecord<K, V>> startConsume(String consumerName, Collection<String> topic) {

        HashMap<String, Object> newMap = new HashMap<String, Object>(consumerProps.get(consumerName));
        try {
            newMap.put(ConsumerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().getHostName() + "-" + String.valueOf(consumerID.getAndIncrement()));
        } catch (UnknownHostException ex) {
            logger.error(ex.toString());
        }

        ReceiverOptions<K, V> receiverOption = ReceiverOptions.create(newMap);
        //receiverOption.commitInterval();
        receiverOption.subscription(topic);

        return KafkaReceiver.create(receiverOption).receive();

    }

    public Flux<SenderResult<Integer>> sendObject(String name, String topic, String key, Object object) {

        KafkaSender<String, Object> producer = getProducer(name);
        if (producer == null) {
            return null;
        }
        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord(topic, key, object), correlationID.getAndIncrement());

        return producer.send(Mono.fromCallable(() -> {
            return record;
        })).doOnError(e -> {
            log.error("Exception", e);
        });
    }




}
