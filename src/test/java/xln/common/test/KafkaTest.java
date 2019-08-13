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
        KafkaSender<String, String> sender = kafkaService.<String, String, StringSerializer, StringSerializer>createProducer("producer0", StringSerializer.class, StringSerializer.class);
        SenderRecord<String, String, Integer> record = SenderRecord.create(new ProducerRecord("test-xln", "1","123"), 1);

        sender.<Integer>send(Mono.fromCallable(()->{return record;})).doOnError(e-> {
            logger.error("Exception", e);}).subscribe(res -> {
                    if(res.correlationMetadata() == 1) {

                    }
            }
        );

        Semaphore lock = new Semaphore(0);

        //ReceiverRecord<String, String> receiverRecord = kafkaService.<String, String>startConsume("kafkaC0", Collections.singletonList("test-xln")).blockLast();

        kafkaService.<String, String>startConsume("kafkaC0", Collections.singletonList("test-xln")).subscribe(r -> {

            logger.info(r.topic());
            logger.info(r.key());
            logger.info(r.value());
            lock.release();

        });

        lock.acquire();
    }
}
