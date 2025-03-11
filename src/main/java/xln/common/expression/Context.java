package xln.common.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class Context {


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

    public Context() {

    }

    public static String getSourceHashKey(String path, Map<String, String> headers, Object body) {

        return new StringBuilder().append(path).append(":").append(headers.hashCode()).append(":").append(body.hashCode()).toString();
    }


    public static abstract class DataProvider implements VariableProvider {


        public abstract CompletableFuture<Object> resolveURL(Context context, String scheme, String host, String path, MultiValueMap<String, ?> params, Map<String, String> headers, Object body);

        @Override
        public Object lookUp(String placeholder) {
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

            path = VariableProvider.patternReplace(path, this);
            var replacedHeaders = new HashMap<String, String>();
            for(var kv : headers.entrySet()) {
                replacedHeaders.put(context.patternReplace(kv.getKey()), context.patternReplace(kv.getValue()));
            }

            body = VariableProvider.patternReplace(body, this);

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
                var key = Context.getSourceHashKey(c.getSrcPath(), c.getSrcHeaders(), c.getSrcBody());
                if (!sources.containsKey(key)) {
                    sources.put(key, provider.resolve(this, c.getSrcPath(), c.getSrcHeaders(), c.getSrcBody()));
                }
            } else if(e instanceof HttpLinkValue) {
                HttpLinkValue value = (HttpLinkValue)e;
                var key = Context.getSourceHashKey(value.getSrcLink(), value.getSrcHeaders(), value.getSrcBody());
                if (!sources.containsKey(key)) {
                    sources.put(key, provider.resolve(this, value.getSrcLink(), value.getSrcHeaders(), value.getSrcBody()));
                }
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
        return VariableProvider.patternReplace(content, provider);
    }




}
