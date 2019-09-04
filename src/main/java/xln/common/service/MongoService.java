package xln.common.service;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.mongodb.*;
import jodd.cli.Cli;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import xln.common.config.MongoConfig;

@Service
@Slf4j
public class MongoService {

    @Autowired
    private MongoConfig config;

    private static final String MONGO_SCHEME = "mongodb://";

    private interface Client {


        <T> T getMongoTemplate(Class<T> type);
        void destroy();


    }

    private static class MongoClient implements Client {

        private SimpleMongoDbFactory mongoDBFactory;
        private MongoTemplate mongoTemplate;

        private SimpleReactiveMongoDatabaseFactory reactiveMongoDBFactory;
        private ReactiveMongoTemplate reactiveMongoTemplate;


        public MongoClient(MongoConfig.MongoServerConfig config) {

            if(config.isNonReactive()) {

                MongoClientOptions.Builder builder = new MongoClientOptions.Builder();
                builder.connectTimeout(config.getConnectionTimeout());
                builder.minConnectionsPerHost(config.getMinHostConnection());
                if (config.getReplSetName() != null) {
                    builder.requiredReplicaSetName(config.getReplSetName());
                }
                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append(MONGO_SCHEME).append(config.getUser()).append(":").append(config.getPw()).
                        append("@").append(config.getHosts()).append("/").append(config.getDatabase()).
                        append("?").append("authSource=admin");

                String mongoURI = strBuilder.toString();

                MongoClientURI mongoClientURI = new MongoClientURI(mongoURI, builder);
                mongoDBFactory = new SimpleMongoDbFactory(mongoClientURI);
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
                        append("@").append(config.getHosts()).append("/").append(config.getDatabase()).
                        append("?").append("authSource=admin").append("&connectTimeoutMS=").append(config.getConnectionTimeout()).append("&minPoolSize=").append(config.getMinHostConnection());

                if(config.getReplSetName() != null) {
                    strBuilder.append("&replicaSet=").append(config.getReplSetName());
                }



                String mongoURI = strBuilder.toString();

                reactiveMongoDBFactory = new SimpleReactiveMongoDatabaseFactory(new ConnectionString(mongoURI));
                reactiveMongoTemplate = new ReactiveMongoTemplate(reactiveMongoDBFactory);
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
    @PostConstruct
    private void init() {

        for (Map.Entry<String, MongoConfig.MongoServerConfig> entry : config.getMongoConfigs().entrySet()) {

            MongoClient newClient = new MongoClient(entry.getValue());
            mongoClients.put(entry.getKey(), newClient);

        }
    }

    @PreDestroy
    private void destroy() {

        for(Map.Entry<String, Client> entry : mongoClients.entrySet()) {
            entry.getValue().destroy();
        }
    }
    private ConcurrentMap<String, Client> mongoClients= new ConcurrentHashMap<>();

    public MongoTemplate getMongoTemplate(String name) {
        return mongoClients.get(name) != null?mongoClients.get(name).getMongoTemplate(MongoTemplate.class):null;
    }

    public <T> T getTemplate(String name, Class<T> type) {
        return mongoClients.get(name) != null?mongoClients.get(name).getMongoTemplate(type):null;
    }


}
