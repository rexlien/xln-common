package xln.common.service;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
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
import reactor.retry.Retry;
import xln.common.config.KafkaConfig;
import xln.common.proto.command.Command;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static reactor.util.retry.Retry.withThrowable;

@Service
@Slf4j
public class KafkaService {

    private final Map<String, KafkaConfig.KafkaProducerConfig> producerConfigs;
    private final Map<String, KafkaConfig.KafkaConsumerConfig> consumerConfigs;

    private final ConcurrentHashMap<String, Map<String, Object>> producerProps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> consumerProps = new ConcurrentHashMap<>();


    private final ConcurrentHashMap<String, KafkaSender<String, Object>> kafkaSenders = new ConcurrentHashMap<>();
    private final AtomicInteger consumerID = new AtomicInteger(0);
    private final AtomicInteger correlationID = new AtomicInteger(0);

    private final ProtoLogService protoLogService;
    private final KafkaConfig kafkaConfig;

    public KafkaService(KafkaConfig kafkaConfig, ProtoLogService protoLogService) {

        this.protoLogService = protoLogService;
        this.kafkaConfig = kafkaConfig;
        producerConfigs = kafkaConfig.getProducerConfigs();
        consumerConfigs = kafkaConfig.getConsumersConfigs();

    }

    @PostConstruct
    private void init() {

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
                props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, kv.getValue().getMaxBlockTime());
                props.put(ProducerConfig.RETRIES_CONFIG, kv.getValue().getRetryCount());

                var security = kv.getValue().getSecurity();
                if(security != null) {
                    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, security.getProtocol());
                    props.put(SaslConfigs.SASL_MECHANISM, security.getMechanism());

                    props.put(SaslConfigs.SASL_JAAS_CONFIG, security.getJaasModule() + " required username=\"" + security.getUserName() + "\" password=\"" + security.getPassword() + "\";");
                }

                producerProps.put(kv.getKey(), props);
            }
        }


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
                log.error(ex.toString());
            }
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kv.getValue().getAutoOffsetResetConfig());
            if (kv.getValue().isEnableAutoCommit()) {
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kv.getValue().isEnableAutoCommit());
                props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, kv.getValue().getAutoCommitInterval());
            }
            consumerProps.put(kv.getKey(), props);

        }
        for(var kv : kafkaConfig.getProducers().entrySet()) {
            try {
                this.createProducer(kv.getKey(), kv.getValue().getConfigName(), Class.forName(kv.getValue().getValueDeserializer()));

            }catch (ClassNotFoundException ex) {
                log.error("", ex);
            }
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
            log.error("", ex);
            return null;
        }

        ReceiverOptions<K, V> receiverOption = ReceiverOptions.create(newMap);
        //receiverOption.commitInterval();
        receiverOption = receiverOption.subscription(topic);

        return  KafkaReceiver.create(receiverOption).receive();

    }

    //safe consume with retry mechinism built-in
    public <K, V> Flux<ReceiverRecord<K, V>> startSafeConsume(String consumerName, Collection<String> topic,
                                                              Consumer<ReceiverRecord<K, V>> processor, Duration maxBackoff) {

        HashMap<String, Object> newMap = new HashMap<String, Object>(consumerProps.get(consumerName));
        try {
            newMap.put(ConsumerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().getHostName() + "-" + String.valueOf(consumerID.getAndIncrement()));
        } catch (UnknownHostException ex) {
            log.error("", ex);
            return null;
        }

        ReceiverOptions<K, V> receiverOption = ReceiverOptions.create(newMap);
        receiverOption = receiverOption.subscription(topic);

        return  KafkaReceiver.create(receiverOption).receive().flatMap( r-> {
            try {
                processor.accept(r);
            }catch (Exception ex) {
                log.error("Kafka retry consume exception:",ex);
                return Flux.error(ex);
            }
            return Flux.just(r);
        }).retryWhen(withThrowable(Retry.any().retryMax(Long.MAX_VALUE).exponentialBackoffWithJitter(Duration.ofSeconds(1), maxBackoff)));

    }

    public ConnectableFlux<SenderResult<Integer>> sendObject(String name, String topic, String key, Object object) {

        KafkaSender<String, Object> producer = getProducer(name);
        if (producer == null) {
            return null;
        }
        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord(topic, key, object), correlationID.getAndIncrement());

        var flux =  producer.send(Mono.fromCallable(() -> {
            return record;
        })).doOnError(e -> {

            log.error("Exception", e);
        }).subscribeOn(Schedulers.boundedElastic()).publish();

        flux.connect();
        return flux;
    }

    public ConnectableFlux<SenderResult<Integer>> sendMessage(String name, String topic, String key, Message message) {

        KafkaSender<String, Object> producer = getProducer(name);
        if (producer == null) {
            log.warn("producer not found");
            return null;
        }

        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord(topic, key, Any.pack(message)), correlationID.getAndIncrement());
        var flux = producer.send(Mono.fromCallable(() -> {
            return record;
        })).doOnError(e -> {

            log.error("Exception", e);

            Command.KafkaMessage kafkaMessage;
            if(key == null) {
                kafkaMessage = Command.KafkaMessage.newBuilder().setTopic(topic).setPayload(Any.pack(message)).build();
            } else {
                kafkaMessage = Command.KafkaMessage.newBuilder().setTopic(topic).setKey(StringValue.of(key)).setPayload(Any.pack(message)).build();
             }
            Command.Retry retryCommand = Command.Retry.newBuilder().setPath("kafka://" + name).setObj(Any.pack(kafkaMessage)).build();
            this.protoLogService.log(Any.pack(retryCommand));

        }).subscribeOn(Schedulers.boundedElastic()).publish();

        flux.connect();

        return flux;

    }

    public List<Flux<ReceiverRecord<String, Object>>> createConsumeStreams(String consumerName, String topic, int numOfConsumer,
                                                                   Duration maxBackoff, Consumer<ReceiverRecord<String, Object>> processor) {
        List<Flux<ReceiverRecord<String, Object>>> fluxes = new LinkedList<>();
        for(int i = 0; i < numOfConsumer; i++) {
            Flux<ReceiverRecord<String, Object>> records = startSafeConsume(consumerName,
                    Collections.singletonList(topic), processor, maxBackoff);
            fluxes.add(records);
        }
        return fluxes;
    }

    //return stream subscribers, caller must dispose it
    public List<Disposable> startConsumerStreams(List<Flux<ReceiverRecord<String, Object>>> streams) {

        var subscribers = new ArrayList<Disposable>();
        for (Flux<ReceiverRecord<String, Object>> flux : streams) {
            subscribers.add(flux.subscribe());
        }
        return subscribers;
    }




}
