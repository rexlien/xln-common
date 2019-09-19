package xln.common.test;

import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import xln.common.utils.CollectionUtils;

import javax.validation.constraints.AssertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Slf4j
public class UtilTest {

    @Test
    public void testPathGet() {
        Map<String, Object> testMap = new HashMap<>();
        Map<String, Object> layer2 = new HashMap<>();
       int layer3 = 1;
        testMap.put("layer2", layer2);
        layer2.put("layer3", layer3);

        Object res = (Integer)CollectionUtils.pathGet("layer2/layer3", testMap);
        Assert.assertTrue((Integer)res == 1);

        res = (Integer)CollectionUtils.pathGet("layer2/layer3/layer4", testMap);
        Assert.assertTrue((Integer)res == null);


        res = (Integer)CollectionUtils.pathGet("layer2/layer4", testMap);
        Assert.assertTrue(res == null);

        res = (Integer)CollectionUtils.pathGet("layer2/layer4/layer5", testMap);
        Assert.assertTrue(res == null);


    }
}
