package xln.common.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.spi.Producer;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class KafkaService
{
    private static Logger logger = LoggerFactory.getLogger(KafkaService.class);

    private ServiceConfig.KafkaProducerConfig producerConfig;

    private KafkaSender<Integer, Object> sender;

    private Map<String, Object> producerProp = new HashMap<>();

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
    }

    public <K, V, T, U> KafkaSender<K, V> createProducer(Class<T> KClass, Class<U> VClass) {

        HashMap<String, Object> newMap = new HashMap<String, Object>(producerProp);
        newMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KClass);
        newMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, VClass);
        SenderOptions<K, V> senderOptions = SenderOptions.create(newMap);

        return KafkaSender.create(senderOptions);

    };



}
