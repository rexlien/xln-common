package xln.common.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.scheduler.Schedulers;
import xln.common.service.RedisService;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class RedisTest {


    private static Logger logger = LoggerFactory.getLogger(RedisTest.class);

    @Autowired
    private RedisService redisService;

    @Test
    public void runScript() {


        redisService.runScript("saddAndGetSize", Collections.singletonList("testKey123"), Collections.singletonList("testValue")).
                publishOn(Schedulers.elastic()).subscribe(l -> {
                    Gson gson = new Gson();
                    Type type = new TypeToken<List<Integer>>(){}.getType();
                    List<Integer> list = gson.fromJson(l.toString(), type);

                    for(int i : list) {
                        logger.warn("res: {}", i);
                    }
                });
    }

}
