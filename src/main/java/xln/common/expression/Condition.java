package xln.common.expression;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Condition implements Element{
    private String srcPath;
    private int op;
    private Object target;
    private String tag;
    private Map<String, String> srcHeaders = new HashMap<>();
    private String body = "";

    public Condition(String srcPath, int op, Object target, String tag) {
        this(srcPath, op, target);
        this.tag = tag;
    }

    public Condition(String srcPath, int op, Object target) {
        this.srcPath = srcPath;
        this.op = op;
        this.target = target;
    }

    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }
}
