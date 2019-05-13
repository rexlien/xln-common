package xln.common.expression;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class Context {

    public Context(DataProvider provider) {

        this.provider = provider;
    }

    public interface DataProvider {

        CompletableFuture<Object> resolve(String path);
    }

    public void gatherSource(Evaluator evaluator, Element root) {

        //HashMap<String, CompletableFuture<Object>> source = new HashMap<>();
        evaluator.traverse(root, (e) -> {
            if(e instanceof Condition) {
                Condition c = (Condition) e;
                sources.put(c.getSrcPath(), provider.resolve(c.getSrcPath()));
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
