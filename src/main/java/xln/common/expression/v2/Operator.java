package xln.common.expression.v2;

import xln.common.expression.Const;
import xln.common.expression.Element;
import xln.common.expression.Evaluator;

import java.util.ArrayList;
import java.util.List;

public class Operator implements Element {

    int op = Const.OP_TYPE_AND;
    private List<Element> operands = new ArrayList<>();


    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.evalRoot(this);
    }
}
