package xln.common.expression;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Array;
import java.util.*;

@Data
@NoArgsConstructor
public class LogicalOperator implements Element {

    private List<Element> elements = new ArrayList<>();
    private int op = Const.OP_TYPE_AND;

    public LogicalOperator(int op, Element... elems) {
        this.op = op;
        addElements(elems);
    }

    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }

    public void addElements(Element... elems ) {
        elements.addAll(Arrays.asList(elems));
    }
}
