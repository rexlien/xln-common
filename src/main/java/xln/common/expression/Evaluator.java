package xln.common.expression;

import xln.common.expression.v2.ValueCondition;

import java.util.Stack;
import java.util.function.Consumer;

public abstract class Evaluator<T> {

    public Evaluator(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return this.context;
    }

    public T startEval(Element root) {
        context.gatherSource(this, root);
        return (T)root.eval(this);
    }

    public void gather(Element root) {
        context.gatherSource(this, root);
    }

    public T evalRoot(Element root) {
        return (T)root.eval(this);
    }

    public void traverse(Element root, Consumer<Element> visitCB, Runnable finishCB) {

        Stack<Element> s = new Stack<>();
        s.push(root);
        while(!s.empty()) {
            Element e =  s.pop();
            if(e instanceof Operator) {
                Operator op = (Operator)e;
                visitCB.accept(e);
                if(op.right != null) {
                    s.push(op.right);
                }
                if(op.left != null) {
                    s.push(op.left);
                }
            } else if(e instanceof LogicalOperator) {
                LogicalOperator op = (LogicalOperator)e;
                visitCB.accept(e);
                for(Element elem : op.getElements()) {
                    s.push(elem);
                }
            } else if(e instanceof ValueCondition) {
                ValueCondition cond = (ValueCondition)e;
                visitCB.accept(e);
                s.push(cond.getSrcValue());
            }
            else {
                visitCB.accept(e);
            }
        }
        if(finishCB != null) {
            finishCB.run();
        }
    }

    abstract T eval(Operator operator);
    abstract T eval(LogicalOperator operator);
    abstract T eval(Condition condition);
    public abstract T eval(Element element);


    protected Context context;

}
