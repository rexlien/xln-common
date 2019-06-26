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

    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }
}
