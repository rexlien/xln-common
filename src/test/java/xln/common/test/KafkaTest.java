package xln.common.test;

import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import xln.common.service.KafkaService;

import java.util.Collections;
import java.util.concurrent.Semaphore;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class KafkaTest
{
    @Autowired
    KafkaService kafkaService;

    private static Logger logger = LoggerFactory.getLogger(KafkaTest.class);

    @Test
    public void testProduceAndConsume() throws InterruptedException {


        Semaphore lock = new Semaphore(0);

        KafkaSender<String, Object> sender = kafkaService.createProducer("producer0", "producer0", JsonSerializer.class);
        SenderRecord<String, Object, Integer> record = SenderRecord.create(new ProducerRecord("test-xln2", null, new Integer(123)), 1);

        sender.send(Mono.fromCallable(()->{return record;})).doOnError(e-> {
            logger.error("Exception", e);}).subscribe(res -> {
                    log.info("send success");
                    if(res.correlationMetadata() == 1) {

                    }
                }
        );

        //ReceiverRecord<String, String> receiverRecord = kafkaService.<String, String>startConsume("kafkaC0", Collections.singletonList("test-xln")).blockLast();

        kafkaService.<String, Object>startConsume("kafkaC0", Collections.singletonList("test-xln2")).publishOn(Schedulers.elastic()).subscribe(r -> {

            logger.info(r.topic());
            logger.info(String.valueOf(r.offset()));
            logger.info(r.key());
            logger.info(r.value().toString());
            r.receiverOffset().commit().block();
            lock.release();

        });



        lock.acquire();
    }
}
