package xln.common.expression;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Condition implements Element{
    private String srcPath;
    private int op;
    private Object target;
    private String tag;

    public Condition(String srcPath, int op, Object target) {
        this.srcPath = srcPath;
        this.op = op;
        this.target = target;
    }

    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }
}
