package xln.common.expression;

public class ConstantValue implements Element, Value {

    public ConstantValue(Object src) {
      this.src = src;
    }
    private Object src;

    @Override
    public Object eval(Evaluator evaluator) {
        return src;
    }
}
