package xln.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;

@Slf4j
public class CollectionUtils {

    public static Object pathGet(String path, Map<String, Object> map)  {
        while(path.startsWith("/")) {
            path = path.substring(1);

        }
        String[] arr = path.split("/");
        Object curObj = map;
        for(int i = 0; i < arr.length; i++) {

            if(curObj instanceof Map) {
                try {
                    Map<String, Object> objMap = (Map<String, Object>) curObj;
                    //check if it's array access path
                    var arrayPath = arr[i];
                    var startIndex = arrayPath.indexOf("[");
                    var endIndex = arrayPath.indexOf("]");
                    if(startIndex >= 0 && endIndex > startIndex + 1) {
                        var key = arrayPath.substring(0, startIndex);
                        curObj = objMap.get(key);
                        if(curObj instanceof ArrayList) {
                            ArrayList<Object> objArray = (ArrayList<Object>) curObj;
                            if(startIndex > 0 && endIndex > startIndex + 1) {
                                var arrIndex = Integer.parseInt(arrayPath.substring(startIndex+1, endIndex));
                                if(arrIndex < objArray.size()) {
                                    curObj = objArray.get(arrIndex);
                                } else {
                                    log.info("index out of bound");
                                    return null;
                                }
                            }
                        }
                    } else {
                        curObj = objMap.get(arr[i]);
                    }

                }catch (Exception ex) {
                    log.error("path get error", ex);
                    return null;
                }
                if(curObj == null) {
                    return null;
                }
            }
            else {
                return null;
            }

        }
        return curObj;

    }

}
