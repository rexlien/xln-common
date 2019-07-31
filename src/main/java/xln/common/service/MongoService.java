package xln.common.service;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.mongodb.WriteConcern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.stereotype.Service;

import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;

import lombok.extern.slf4j.Slf4j;
import xln.common.config.MongoConfig;

@Service
@Slf4j
public class MongoService {

    @Autowired
    private MongoConfig config;

    public static class MongoServer {

        private SimpleMongoDbFactory mongoDBFactory;
        private MongoTemplate mongoTemplate;

        private static final String MONGO_SCHEME = "mongodb://";

        public MongoServer(MongoConfig.MongoServerConfig config) throws URISyntaxException{

            MongoClientOptions.Builder builder = new MongoClientOptions.Builder();
            builder.connectTimeout(config.getConnectionTimeout());
            builder.minConnectionsPerHost(config.getMinHostConnection());
            if(config.getReplSetName() != null) {
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

        public MongoTemplate getMongoTemplate() {
            return mongoTemplate;
        }

        private void destroy() {
            try {
                mongoDBFactory.destroy();
            } catch (Exception e) {
                log.error("mongo db destroy failed", e);
            }
        }

    }

    @PostConstruct
    private void init() {

        for (Map.Entry<String, MongoConfig.MongoServerConfig> entry : config.getMongoConfigs().entrySet()) {
            try {
                MongoServer newServer = new MongoServer(entry.getValue());
                mongoServers.put(entry.getKey(), newServer);
            } catch (URISyntaxException e ) {
                log.error("uri syntax error", e);
            }

        }
    }

    @PreDestroy
    private void destroy() {

        for(Map.Entry<String, MongoServer> entry : mongoServers.entrySet()) {
            entry.getValue().destroy();
        }
    }
    private ConcurrentMap<String, MongoServer> mongoServers = new ConcurrentHashMap<>();

    public MongoTemplate getMongoTemplate(String name) {
        return mongoServers.get(name) != null?mongoServers.get(name).getMongoTemplate():null;
    }


}
