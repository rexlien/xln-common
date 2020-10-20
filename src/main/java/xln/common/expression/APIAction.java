package xln.common.expression;

import org.springframework.http.HttpMethod;
import xln.common.utils.HttpUtils;

import java.util.HashMap;
import java.util.Map;

public class APIAction implements Action {

    private String method = "POST";
    private String path = "";
    private Map<String, String> headers = new HashMap<>();
    private String body = "";

    public Map<String, String> getHeaders() {
        return headers;
    }

    public APIAction setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public String getBody() {
        return body;
    }

    public APIAction setBody(String body) {
        this.body = body;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public APIAction setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getPath() {
        return path;
    }

    public APIAction setPath(String path) {
        this.path = path;
        return this;
    }

    @Override
    public Object invoke(Context context) {
        String resolvedPath = context.patternReplace(path);
        String resolvedBody = context.patternReplace(body);
        var resolvedHeaders = new HashMap<String, String>();
        for(var kv : headers.entrySet()) {
            resolvedHeaders.put(context.patternReplace(kv.getKey()), context.patternReplace(kv.getValue()));
        }
        return HttpUtils.httpCallMonoResponseEntity(resolvedPath, null, HttpMethod.resolve(method), Object.class, resolvedHeaders, resolvedBody);
    }

    @Override
    public Object eval(Evaluator evaluator) {
        try {
            return invoke(evaluator.getContext());
        }catch (Exception ex) {
            return null;
        }
    }
}
