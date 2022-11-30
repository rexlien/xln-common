package xln.common.expression.v2;

import xln.common.expression.Const;
import xln.common.expression.Element;
import xln.common.expression.Evaluator;


//works like condition but allows general value type(any type that can be evaluated to be compared) as source value
public class ValueCondition implements Element {

    int op = Const.OP_TYPE_GREATER_OR_EQUAL;
    private Element srcValue;
    private Object targetValue;
    private String tag;

    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }

    public int getOp() {
        return op;
    }

    public ValueCondition setOp(int op) {
        this.op = op;
        return this;
    }

    public Element getSrcValue() {
        return srcValue;
    }

    public ValueCondition setSrcValue(Element srcValue) {
        this.srcValue = srcValue;
        return this;
    }

    public Object getTargetValue() {
        return targetValue;
    }

    public ValueCondition setTargetValue(Object targetValue) {
        this.targetValue = targetValue;
        return this;
    }

    public String getTag() {
        return tag;
    }

    public ValueCondition setTag(String tag) {
        this.tag = tag;
        return this;
    }
}
