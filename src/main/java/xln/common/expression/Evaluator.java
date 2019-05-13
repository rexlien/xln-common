package xln.common.expression;

import java.util.function.Consumer;

public interface Evaluator {
    void traverse(Element root, Consumer<Element> visitCB, Runnable finishCB);
    Object eval(Operator elem);
    boolean eval(Condition condition);
}
