package xln.common.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
public class Context {

    public static final String REGEX_PATTERN = "(\\$\\{[^}]+\\})";
    private Map<String, Object> contextMap = new HashMap<>();


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
                m.appendReplacement(sb, (provider != null)?provider.getPathReplacement(placeholder):"");
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public static abstract class DataProvider {


        public abstract CompletableFuture<Object> resolveURL(Context context, String scheme, String host, String path, MultiValueMap<String, ?> params);

        public String getPathReplacement(String placeholder) {
            return "";
        }

        public void initialize(Context context) {

        }


        public CompletableFuture<Object> resolve(Context context, String path) {

            if(path == null) {

                CompletableFuture<Object> future = new CompletableFuture<>();
                future.complete(null);
                return future;
            }

            path = patternReplace(path, this);

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
                return resolveURL(context, uri.getScheme(), uri.getHost(), uri.getPath(),  parameters);
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
                sources.put(c.getSrcPath(), provider.resolve(this, c.getSrcPath()));
            }
        }, null);

    }

    public CompletableFuture<Void> gatherSourceJoin(Evaluator evaluator, Element root) {

        gatherSource(evaluator, root);
        List<CompletableFuture<Object>> list = sources.values().stream().collect(Collectors.toList());
        return CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]));
    }

    public CompletableFuture<Object> getSource(String path) {
        return sources.get(path);
    }


    private HashMap<String, CompletableFuture<Object>> sources = new HashMap<>();

    //private Evaluator evaluator;
    private DataProvider provider;

}
