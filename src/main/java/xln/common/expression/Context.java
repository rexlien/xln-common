package xln.common.expression;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
public class Context {

    public static final String REGEX_PATTERN = "(\\$\\{[^}]+\\})";
    private Map<String, Object> contextMap = new HashMap<>();

    //private Evaluator evaluator;
    private DataProvider provider;

    //use for cache condition source body as map
    private ConcurrentHashMap<Condition, Map<Object, Object>> conditionSrcCache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, CompletableFuture<Object>> sources = new ConcurrentHashMap<>();


    public Context(DataProvider provider) {

        this.provider = provider;
        this.provider.initialize(this);

    }

    public static String patternReplace(String path, DataProvider provider) {

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
                m.appendReplacement(sb, (provider != null)?provider.getPathReplacement(placeholder):"");
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public static Object patternReplace(Object obj, DataProvider provider) {
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

    public static String getSourceHashKey(String path, Map<String, String> headers, Object body) {

        return new StringBuilder().append(path).append(":").append(headers.hashCode()).append(":").append(body.hashCode()).toString();
    }

    public static abstract class DataProvider {


        public abstract CompletableFuture<Object> resolveURL(Context context, String scheme, String host, String path, MultiValueMap<String, ?> params, Map<String, String> headers, Object body);

        public String getPathReplacement(String placeholder) {
            return "";
        }

        public void initialize(Context context) {

        }


        public CompletableFuture<Object> resolve(Context context, String path, Map<String, String> headers, Object body) {

            if(path == null) {

                CompletableFuture<Object> future = new CompletableFuture<>();
                future.complete(null);
                return future;
            }

            path = patternReplace(path, this);
            var replacedHeaders = new HashMap<String, String>();
            for(var kv : headers.entrySet()) {
                replacedHeaders.put(context.patternReplace(kv.getKey()), context.patternReplace(kv.getValue()));
            }

            body = patternReplace(body, this);

            URI uri;
            try {
                uri = new URI(path);
            }catch (Exception ex) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                log.warn("", ex);
                future.complete(null);
                return future;
            }
            if(uri.getScheme() != null) {

                MultiValueMap<String, String> parameters =
                        UriComponentsBuilder.fromUri(uri).build().getQueryParams();

                //String key = parameters.getFirst("key");
                var host = uri.getHost();
                if(uri.getPort() != -1) {
                    host += ":" + String.valueOf(uri.getPort());
                }
                return resolveURL(context, uri.getScheme(), host, uri.getPath(),  parameters, replacedHeaders, body);
            } else {

                log.warn("path needs to have scheme");
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.complete(null);
                return future;
            }
        }

    }

    public Map<String, Object> getContextMap() {
        return contextMap;
    }

    public void gatherSource(Evaluator evaluator, Element root) {

        //HashMap<String, CompletableFuture<Object>> source = new HashMap<>();
        evaluator.traverse(root, (e) -> {
            if(e instanceof Condition) {
                Condition c = (Condition) e;
                sources.put(Context.getSourceHashKey(c.getSrcPath(), c.getSrcHeaders(), c.getSrcBody()), provider.resolve(this, c.getSrcPath(), c.getSrcHeaders(), c.getSrcBody()));
            }
        }, null);

    }

    public CompletableFuture<Void> gatherSourceJoin(Evaluator evaluator, Element root) {

        gatherSource(evaluator, root);
        List<CompletableFuture<Object>> list = sources.values().stream().collect(Collectors.toList());
        return CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]));
    }

    public CompletableFuture<Object> getSource(String path, Map<String, String> headers, Object body) {

        return sources.get(Context.getSourceHashKey(path, headers, body));
    }



    public String patternReplace(String content) {
        return Context.patternReplace(content, provider);
    }




}
