package xln.common.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Context {

    private Map<String, Map<String, Object>> contextMap = new HashMap<>();


    public Context(DataProvider provider) {

        this.provider = provider;
        this.provider.initialize(this);

    }

    public static abstract class DataProvider {

        public static final String REGEX_PATTERN = "(\\$\\{[^}]+\\})";
        public abstract CompletableFuture<Object> resolveURL(Context context, String scheme, String path, MultiValueMap params);

        public String getPathReplacement(String placeholder) {
            return "";
        }

        public void initialize(Context context) {

        }


        private String patternReplace(String path) {

            if(path == null) {
                return null;
            }

            Pattern p = Pattern.compile(REGEX_PATTERN);
            Matcher m = p.matcher(path);
            StringBuffer sb = new StringBuffer();

            while(m.find()) {

                String placeholder = m.group(1);
                if(placeholder != null) {
                    m.appendReplacement(sb, getPathReplacement(placeholder));
                }
            }
            m.appendTail(sb);

            return sb.toString();
        }

        public CompletableFuture<Object> resolve(Context context, String path) {

            if(path == null) {

                CompletableFuture<Object> future = new CompletableFuture<>();
                future.complete(null);
                return future;
            }

            path = patternReplace(path);

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
                return resolveURL(context, uri.getScheme(), uri.getPath(),  parameters);
            } else {

                log.warn("path needs to have scheme");
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.complete(null);
                return future;
            }
        }

    }

    public Map<String, Map<String, Object>> getContextMap() {
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

    public CompletableFuture<Object> getSource(String path) {
        return sources.get(path);
    }


    private HashMap<String, CompletableFuture<Object>> sources = new HashMap<>();

    //private Evaluator evaluator;
    private DataProvider provider;

}
