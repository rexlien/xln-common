package xln.common.expression;

import com.google.gson.Gson;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface VariableProvider {

    static final String REGEX_PATTERN
            = "(\\$\\{[^}]+\\})";
    static String patternReplace(String path, VariableProvider provider) {

        if(path == null) {
            return null;
        }

        Pattern p = Pattern.compile(REGEX_PATTERN);
        Matcher m = p.matcher(path);
        StringBuffer sb = new StringBuffer();

        while(m.find()) {

            String placeholder = m.group(1);
            if(placeholder != null) {
                placeholder = placeholder.substring(2, placeholder.length() - 1);
                m.appendReplacement(sb, (provider != null)?provider.lookUp(placeholder).toString():"");
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    static Object patternReplace(Object obj, VariableProvider provider) {
        if(obj instanceof String) {
            return patternReplace((String)obj, provider);
        } else {

            Gson gson = new Gson();
            var json = gson.toJson(obj);
            json = patternReplace(json, provider);
            obj = gson.fromJson(json, obj.getClass());
            return obj;
        }

    }

    Object lookUp(String key);
}
