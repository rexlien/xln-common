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
    public static final int OP_TYPE_AND = 0;
    public static final int OP_TYPE_OR = 1;
    public static final int OP_TYPE_EQUAL = 2;
    public static final int OP_TYPE_GREATER = 3;
    public static final int OP_TYPE_LESS = 4;
    public static final int OP_TYPE_CONTAINS = 5;
    public static final int OP_TYPE_GREATER_OR_EQUAL = 6;
    public static final int OP_TYPE_LESS_OR_EQUAL = 7;

    public Operator(Element left) {
        this.left = left;
    }

    int op = OP_TYPE_AND;

    Element left;
    Element right;

    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }
}
