package xln.common.expression;

import lombok.Data;
import lombok.NoArgsConstructor;

/*
null & null -> true
left & null -> left
 */

@Data
@NoArgsConstructor
public class Operator implements Element {

    public Operator(Element left) {
        this.left = left;
    }

    int op = Const.OP_TYPE_AND;

    Element left;
    Element right;

    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }

   
}
