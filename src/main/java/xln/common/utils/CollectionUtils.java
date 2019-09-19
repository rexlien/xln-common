package xln.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
public class CollectionUtils {

    public static Object pathGet(String path, Map<String, Object> map)  {
        String[] arr = path.split("/");
        Object curObj = map;
        for(int i = 0; i < arr.length; i++) {

            if(curObj instanceof Map) {
                try {
                    Map<String, Object> objMap = (Map<String, Object>) curObj;
                    curObj = objMap.get(arr[i]);

                }catch (Exception ex) {
                    log.error("path get type error", ex);
                    return null;
                }
                if(curObj == null) {
                    return null;
                }

            } else {

                return null;

            }

        }
        return curObj;

    }

}
