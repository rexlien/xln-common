package xln.common.service;


import com.mongodb.ConnectionString;
import com.mongodb.MongoClientOptions;
import com.mongodb.WriteConcern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Service;
import xln.common.config.MongoConfig;
import xln.common.config.MongoConfig.MongoServerConfig;
import xln.common.converter.ApiReader;
import xln.common.converter.ProtobufAnyReader;
import xln.common.converter.ProtobufWriter;
import xln.common.grpc.ProtobufService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MongoService {

    private final MongoConfig config;

    private ConcurrentMap<String, Client> mongoClients= new ConcurrentHashMap<>();
    private volatile MongoClient springClient;
    private static final String MONGO_SCHEME = "mongodb://";
    private final ProtobufService protobufService;
    //private volatile String springTemplate;

    private interface Client {
        <T> T getMongoTemplate(Class<T> type);
        void destroy();
    }

    private static class MongoClient implements Client {

        private SimpleMongoClientDatabaseFactory mongoDBFactory;
        private MongoTemplate mongoTemplate;

        private SimpleReactiveMongoDatabaseFactory reactiveMongoDBFactory;
        private ReactiveMongoTemplate reactiveMongoTemplate;


        public MongoClient(MongoServerConfig config, ProtobufService protobufService) {

            var joinedEndPoint = String.join(",", config.getEndPoint().hosts);

            if(config.isNonReactive()) {

                MongoClientOptions.Builder builder = new MongoClientOptions.Builder();
                builder.connectTimeout(config.getConnectionTimeout());
                builder.minConnectionsPerHost(config.getMinHostConnection());
                if (config.getReplSetName() != null) {
                    builder.requiredReplicaSetName(config.getReplSetName());
                }
                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append(MONGO_SCHEME).append(config.getUser()).append(":").append(config.getPw()).
                        append("@").append(joinedEndPoint).append("/").append(config.getDatabase()).
                        append("?").append("authSource=admin");

                String mongoURI = strBuilder.toString();

                //MongoClientURI mongoClientURI = new MongoClientURI(mongoURI, builder);
                mongoDBFactory = new SimpleMongoClientDatabaseFactory(mongoURI);
                mongoTemplate = new MongoTemplate(mongoDBFactory);

                if (config.getWriteConcern() != null) {
                    String w = config.getWriteConcern();
                    try {
                        int wInt = Integer.parseInt(w);
                        mongoTemplate.setWriteConcern(new WriteConcern(wInt, config.getWriteAckTimeout()));

                    } catch (NumberFormatException ex) {
                        mongoTemplate.setWriteConcern(new WriteConcern(w).withWTimeout(config.getWriteAckTimeout(), TimeUnit.MILLISECONDS));
                    }

                }
            }

            if(config.isReactive()) {

                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append(MONGO_SCHEME).append(config.getUser()).append(":").append(config.getPw()).
                        append("@").append(joinedEndPoint).append("/").append(config.getDatabase()).
                        append("?").append("authSource=admin").append("&connectTimeoutMS=").append(config.getConnectionTimeout()).append("&minPoolSize=").append(config.getMinHostConnection());

                if(config.getReplSetName() != null) {
                    strBuilder.append("&replicaSet=").append(config.getReplSetName());
                }



                String mongoURI = strBuilder.toString();

                reactiveMongoDBFactory = new SimpleReactiveMongoDatabaseFactory(new ConnectionString(mongoURI));
                MongoCustomConversions conversions = new MongoCustomConversions(Arrays.asList(new ProtobufWriter(protobufService.getTypeRegistry()),
                        new ApiReader(), new ProtobufAnyReader(protobufService.getTypeRegistry())));

                MongoMappingContext context = new MongoMappingContext();
                context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
                context.afterPropertiesSet();

                MappingMongoConverter mongoConverter = new MappingMongoConverter(ReactiveMongoTemplate.NO_OP_REF_RESOLVER, context);
                mongoConverter.setCustomConversions(conversions);
                mongoConverter.setCodecRegistryProvider(this.reactiveMongoDBFactory);
                mongoConverter.afterPropertiesSet();

                reactiveMongoTemplate = new ReactiveMongoTemplate(reactiveMongoDBFactory, mongoConverter);
                if (config.getWriteConcern() != null) {
                    String w = config.getWriteConcern();
                    try {
                        int wInt = Integer.parseInt(w);
                        reactiveMongoTemplate.setWriteConcern(new WriteConcern(wInt, config.getWriteAckTimeout()));

                    } catch (NumberFormatException ex) {
                        reactiveMongoTemplate.setWriteConcern(new WriteConcern(w).withWTimeout(config.getWriteAckTimeout(), TimeUnit.MILLISECONDS));
                    }

                }
            }


        }
/*
        public MongoTemplate getMongoTemplate() {
            return mongoTemplate;
        }
*/
        public <T> T getMongoTemplate(Class<T> type) {
            if(type.equals(MongoTemplate.class)) {
                return type.cast(mongoTemplate);
            } else if(type.equals(ReactiveMongoTemplate.class)) {
                return type.cast(reactiveMongoTemplate);
            }
            return null;
        }



         public void destroy() {
            try {
                if(mongoDBFactory != null) {
                    mongoDBFactory.destroy();
                }
                if(reactiveMongoDBFactory != null) {
                    reactiveMongoDBFactory.destroy();
                }

            } catch (Exception e) {
                log.error("mongo db destroy failed", e);
            }
        }

    }
/*
    private static class ReactiveMongoClint {

        private SimpleReactiveMongoDatabaseFactory mongoDBFactory;
        private ReactiveMongoTemplate mongoTemplate;
        private static final String MONGO_SCHEME = "mongodb://";

        public ReactiveMongoClint(MongoConfig.MongoServerConfig config) {



            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(MONGO_SCHEME).append(config.getUser()).append(":").append(config.getPw()).
                    append("@").append(config.getHosts()).append("/").append(config.getDatabase()).
                    append("?").append("authSource=admin").append("&connectTimeoutMS=").append(config.getConnectionTimeout()).
                    append("&replicaSet=").append(config.getReplSetName()).append("&minPoolSize=").append(config.getMinHostConnection());

            String mongoURI = strBuilder.toString();

            mongoDBFactory = new SimpleReactiveMongoDatabaseFactory(new ConnectionString(mongoURI));
            mongoTemplate = new ReactiveMongoTemplate(mongoDBFactory);
            if(config.getWriteConcern() != null) {
                String w = config.getWriteConcern();
                try {
                    int wInt = Integer.parseInt(w);
                    mongoTemplate.setWriteConcern(new WriteConcern(wInt, config.getWriteAckTimeout()));

                }catch (NumberFormatException ex) {
                    mongoTemplate.setWriteConcern(new WriteConcern(w).withWTimeout(config.getWriteAckTimeout(), TimeUnit.MILLISECONDS));
                }

            }

        }

        public <T> T getMongoTemplate(Class<T> type) {
            if(type.equals(ReactiveMongoTemplate.class)) {
                return type.cast(mongoTemplate);
            }
            return null;
        }

        public void destroy() {
            try {
                mongoDBFactory.destroy();
            } catch (Exception e) {
                log.error("mongo db destroy failed", e);
            }
        }
    }
*/

    public MongoService(MongoConfig config, ProtobufService protobufService) {
        this.config = config;
        this.protobufService = protobufService;

    }

    @PostConstruct
    private void init() {

        for (Map.Entry<String, MongoServerConfig> entry : config.getMongoConfigs().entrySet()) {

            MongoClient newClient = new MongoClient(entry.getValue(), protobufService);
            mongoClients.put(entry.getKey(), newClient);
            if(config.getAutoConfigSpringTemplate().equals(entry.getKey())) {
                springClient = newClient;
            }

        }
    }

    @PreDestroy
    private void destroy() {

        for(Map.Entry<String, Client> entry : mongoClients.entrySet()) {
            entry.getValue().destroy();
        }
    }


    public MongoTemplate getMongoTemplate(String name) {
        return mongoClients.get(name) != null?mongoClients.get(name).getMongoTemplate(MongoTemplate.class):null;
    }

    public ReactiveMongoTemplate getReactiveMongoTemplate(String name) {
        return mongoClients.get(name) != null?mongoClients.get(name).getMongoTemplate(ReactiveMongoTemplate.class):null;
    }

    public <T> T getTemplate(String name, Class<T> type) {
        return mongoClients.get(name) != null?mongoClients.get(name).getMongoTemplate(type):null;
    }

    public <T> T getSpringTemplate(Class<T> type) {
        return springClient != null?springClient.getMongoTemplate(type):null;
    }

    //public MongoTemplate


}
