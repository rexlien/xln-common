package xln.common.test;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.index.IndexDefinitionProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.service.MongoService;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class MongoTest {

    @Autowired
    private MongoService mongoService;

    @Document
    @Data
    public static class TestData {
        @Id
        private ObjectId id;

        //@TextIndexed
        private String name;

        @Data
        public static class Child {

            private String id = "testChild";
            private int type = 0;
            private HashMap<String, Child> children = new HashMap<>();
        }

        HashMap<String, Child> children = new HashMap<>();

    }



    @Test
    public void TestOperations() {

        MongoTemplate template = mongoService.getMongoTemplate("mongo0");

        template.dropCollection(TestData.class);
        //template.createCollection(TestData.class);
        IndexDefinition def = new Index().on("name", Sort.Direction.ASC).unique();

        template.indexOps(TestData.class).ensureIndex(def);
        TestData data = new TestData();
        data.setName("root");
        data.children.put("child0", new TestData.Child());
        template.insert(data);

        data = new TestData();
        data.setName("root2");
        TestData.Child child = new TestData.Child();
        child.children.put("grandChild0", new TestData.Child());
        data.children.put("child0", child);
        template.insert(data);

        //template.save(data);

        data = template.findOne(Query.query(Criteria.where("name").is("root2")), TestData.class);
        if(data != null) {
            log.info("found");
            log.info(data.getName());
            log.info(data.getId().toHexString());
            log.info(data.getChildren().get("child0").getId());
            log.info(data.getChildren().get("child0").getChildren().get("grandChild0").getId());
        }


    }

}
