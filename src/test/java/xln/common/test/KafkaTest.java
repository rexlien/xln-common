package xln.common.test;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import io.netty.util.internal.ConcurrentSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.util.retry.Retry;
import xln.common.config.KafkaConfig;
import xln.common.proto.command.Command;
import xln.common.serializer.ProtoKafkaSerializer;
import xln.common.service.KafkaService;
import xln.common.service.ProtoLogService;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class KafkaTest
{

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        //
        kafka.start();

        //var bootstrap = kafka.getBootstrapServers();
        registry.add("xln.kafka-config.producerConfigs.producer0.serverUrls", kafka::getBootstrapServers);
        registry.add("xln.kafka-config.producerConfigs.producer1.serverUrls", kafka::getBootstrapServers);

        registry.add("xln.kafka-config.consumersConfigs.kafkaC0.serverUrls", kafka::getBootstrapServers);
        registry.add("xln.kafka-config.consumersConfigs.kafkaC1.serverUrls", kafka::getBootstrapServers);

    }
    @Autowired
    private KafkaService kafkaService;

    @Autowired
    private ProtoLogService logService;

    private static Logger logger = LoggerFactory.getLogger(KafkaTest.class);

    private KafkaSender<String, Object> sender;

    private boolean inited = false;

    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));


    @Before
    public void setup() {

        if(!inited) {
            sender = kafkaService.createProducer("producer0", "producer0", JsonSerializer.class);
            kafkaService.createProducer("producer1", "producer1", ProtoKafkaSerializer.class);
            inited = true;
        }
    }



    @Test
    public void testProduceAndConsume() throws InterruptedException {


        Semaphore lock = new Semaphore(0);
        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord("testProduceAndConsume", null, new Integer(123)), 1);

        sender.send(Mono.fromCallable(()->{return record;})).doOnError(e-> {
            logger.error("Exception", e);}).subscribe(res -> {
                    log.info("send success");
                    if(res.correlationMetadata() == 1) {

                    }
                }
        );

        //ReceiverRecord<String, String> receiverRecord = kafkaService.<String, String>startConsume("kafkaC0", Collections.singletonList("test-xln")).blockLast();

        kafkaService.<String, Object>startConsume("kafkaC0", Collections.singletonList("testProduceAndConsume")).publishOn(Schedulers.boundedElastic()).subscribe(r -> {


            logger.info(r.topic());
            logger.info(String.valueOf(r.offset()));
            logger.info(r.key());
            logger.info(r.value().toString());
            r.receiverOffset().commit().block();
            lock.release();

        });



        lock.acquire();
    }

    @Test
    public void testConsumeException() throws Exception {

        Semaphore lock = new Semaphore(0);

        ConcurrentHashMap<Integer, Integer> testProcessed = new ConcurrentHashMap<>();

        for(int i = 0; i < 10; i++) {
            kafkaService.sendObject("producer0", "testConsumeException", null, i);
            testProcessed.put(i, i);
        }

        var random = new Random();
        kafkaService.<String, Object>startConsume("kafkaC0", Collections.singletonList("testConsumeException")).publishOn(Schedulers.boundedElastic()).flatMap(r -> {

            logger.info(r.topic());
            logger.info(String.valueOf(r.offset()));
            logger.info(r.key());


            if (random.nextFloat()  > 0.5f) {
                log.error("Exception throwing");
                return Flux.error(new IllegalArgumentException("Exception"));
            }

            r.receiverOffset().acknowledge();

            Assert.assertTrue(testProcessed.contains(r.value()));
            testProcessed.remove(r.value());
            if(testProcessed.isEmpty()) {
                lock.release();
            }

            return Flux.just(r);

        }).retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(3))).subscribe();

        lock.acquire();

    }

    @Test
    public void testProtoKafka()  throws InterruptedException {

        Semaphore lock = new Semaphore(0);

       kafkaService.sendMessage("producer1", "testProtoKafka", null, Command.TestKafkaPayLoad.newBuilder().setPayload("hello").build());

        kafkaService.<String, Object>startConsume("kafkaC1", Collections.singletonList("testProtoKafka")).publishOn(Schedulers.elastic()).subscribe(r -> {

            logger.info(r.topic());
            logger.info(String.valueOf(r.offset()));
            logger.info(r.key());

            Any any = (Any)r.value();
            if(any.is(Command.TestKafkaPayLoad.class)) {
                try {
                    Command.TestKafkaPayLoad message = any.unpack(Command.TestKafkaPayLoad.class);
                    log.info(message.getPayload());
                } catch (InvalidProtocolBufferException ex) {

                }
            }
            r.receiverOffset().commit().block();
            lock.release();

        });

        lock.acquire();


    }

    @Test
    public void testFailedRetry() {
        KafkaSender<String, Object> sender = kafkaService.createProducer("producer1", "producer1", ProtoKafkaSerializer.class);
        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord("testFailedRetry", null, new Integer(123)), 1);

        sender.send(Mono.fromCallable(()->{return record;})).doOnError(e-> {
            logger.error("Exception", e);}).subscribe(res -> {
                    log.info("send success");
                    if(res.correlationMetadata() == 1) {

                    }
                }
        );


    }

    @Test
    public void testKafkaMessage() {

        Command.KafkaMessage kafkaMessage = Command.KafkaMessage.newBuilder().setTopic("testKafkaMessage").setPayload(Any.pack(Command.TestKafkaPayLoad.newBuilder().setPayload("hello").setPayload2("hello2").build())).build();
        Command.Retry retryCommand = Command.Retry.newBuilder().setPath("kafka://" + "kafka0").setObj(Any.pack(kafkaMessage)).build();

        logService.log(Any.pack(retryCommand));
        logService.log(Any.pack(kafkaMessage));


        logService.iterateLog((k, v) -> {

            if(v.is(Command.KafkaMessage.class)) {

                try {
                    Command.KafkaMessage command = v.unpack(Command.KafkaMessage.class);
                    Any payload = command.getPayload();
                    if(payload.is(Command.TestKafkaPayLoad.class)) {
                        Command.TestKafkaPayLoad msg = payload.unpack(Command.TestKafkaPayLoad.class);
                        Assert.assertTrue(msg.getPayload().equals("hello"));
                    }

                }catch (InvalidProtocolBufferException ex) {

                }

            } else if(v.is(Command.Retry.class)) {
                try {
                    Command.Retry retry = v.unpack(Command.Retry.class);
                    if(retry.getObj().is(Command.KafkaMessage.class)) {

                        Command.KafkaMessage command = retry.getObj().unpack(Command.KafkaMessage.class);
                        Any payload = command.getPayload();
                        if(payload.is(Command.TestKafkaPayLoad.class)) {
                            Command.TestKafkaPayLoad msg = payload.unpack(Command.TestKafkaPayLoad.class);
                            Assert.assertTrue(msg.getPayload().equals("hello"));
                        }
                    }

                }catch (InvalidProtocolBufferException ex) {

                }

            }
            logService.deleteLog(k);
        });

    }
}
