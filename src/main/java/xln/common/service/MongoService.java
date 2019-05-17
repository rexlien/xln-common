package xln.common.service;


import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import lombok.extern.slf4j.Slf4j;
import org.redisson.misc.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.stereotype.Service;
import xln.common.config.CacheConfig;
import xln.common.config.MongoConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class MongoService {

    @Autowired
    private MongoConfig config;

    public static class MongoServer {

        private SimpleMongoDbFactory mongoDBFactory;
        private MongoTemplate mongoTemplate;

        private static final String MONGO_SCHEME = "mongodb";

        public MongoServer(MongoConfig.MongoServerConfig config) throws URISyntaxException{

            MongoClientOptions.Builder builder = new MongoClientOptions.Builder();
            builder.connectTimeout(config.getConnectionTimeout());
            builder.minConnectionsPerHost(config.getMinHostConnection());


            URI uri = new URI(MONGO_SCHEME, config.getUser() + ":" + config.getPw(), config.getHost(), config.getPort(),
                    "/" + config.getDatabase(), "authSource=admin", null);
            MongoClientURI mongoClientURI = new MongoClientURI(uri.toString(), builder);
            mongoDBFactory = new SimpleMongoDbFactory(mongoClientURI);
            mongoTemplate = new MongoTemplate(mongoDBFactory);

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
        return mongoServers.get(name).getMongoTemplate();
    }


}