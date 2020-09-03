package xln.common.expression;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Stack;

@Slf4j
public class Utils {

    public static <T> Number castNumber(Class<T>toType, Object src) {

        if(!Number.class.isAssignableFrom(toType)) {
            return null;
        }

        if(!toType.isAssignableFrom(src.getClass())) {

            if(src instanceof Number) {
                Number num = (Number)src;
                if (toType == Integer.class) {
                    return num.intValue();
                } else if (toType == Double.class) {
                    return num.doubleValue();
                } else if (toType == Long.class) {
                    return num.longValue();
                } else if (toType == Float.class) {
                    return num.floatValue();
                } else {
                    return null;
                }
            } else if(src instanceof String) {

                String str = (String)src;
                try {
                    if (toType == Integer.class) {
                        return Integer.parseInt(str);
                    } else if (toType == Double.class) {
                        return Double.parseDouble(str);
                    } else if (toType == Long.class) {
                        return Long.parseLong(str);
                    } else if (toType == Float.class) {
                        return Float.parseFloat(str);
                    } else {
                        return null;
                    }
                }catch (NumberFormatException ex) {
                    log.info("parse number exception when castNumber", ex);
                    return null;
                }
            }
        }
        return (Number)(src);
    }

    //index result by its tag
    public static HashMap<String, Result> mapResult(Result root) {

        HashMap<String, Result> ret = new HashMap();
        Stack<Result> resultStack = new Stack<>();

        resultStack.push(root);
        while(!resultStack.empty()) {

            var result = resultStack.pop();
            if(result.getTag() != null) {
                ret.put(result.getTag(), result);
            }

            for(var child : result.getChildren()) {
                resultStack.push(child);
            }
        }

        return ret;
    }


}
