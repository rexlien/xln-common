package xln.common.expression;

import lombok.extern.slf4j.Slf4j;

import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class ConditionEvaluator implements Evaluator{

    public ConditionEvaluator(Context context) {
        this.context = context;
    }

    public Object startEval(Element root) {

        context.gatherSource(this, root);

        return root.eval(this);

    }

    //implementation assuming all boolean
    public Object eval(Operator operator) {
        if(operator.left == null && operator.right == null) {
            return true;
        }
        else if(operator.left != null && operator.right != null) {
            if(operator.op == Operator.OP_TYPE_AND) {
                return (boolean) operator.left.eval(this) && (boolean) operator.right.eval(this);
            } else {
                return (boolean) operator.left.eval(this) || (boolean) operator.right.eval(this);
            }
        }
        else if(operator.left != null) {
            return operator.left.eval(this);
        }
        else {
            return operator.right.eval(this);
        }
    }

    @Override
    public boolean eval(LogicalOperator operator) {
        if(operator.getOp() != Operator.OP_TYPE_AND && operator.getOp() != Operator.OP_TYPE_OR) {
            log.error("Operator type can only be OR or AND");
            return false;
        }
        if(operator.getElements() == null || operator.getElements().isEmpty()) {
            return true;
        }
        Boolean ret = null;
        for(Element elem : operator.getElements()) {

            if(elem instanceof Condition || elem instanceof Operator) {
                if (ret == null) {
                    ret = (boolean) elem.eval(this);
                } else {
                    if (operator.getOp() == Operator.OP_TYPE_AND) {
                        ret &= (boolean) elem.eval(this);
                    } else {
                        ret |= (boolean) elem.eval(this);
                    }
                }
            } else {
                log.error("element must be Condition or Operator");
                return false;
            }
        }
        return ret;
    }


    public boolean eval(Condition condition) {
        Object src = null;
        try {
            src = context.getSource(condition.getSrcPath()).get(10, TimeUnit.SECONDS);
        }catch (Exception e) {
            log.error("could not get context source", e);
        }
        if(src != null) {

            //only same type is comparable
            if(src.getClass() !=  condition.getTarget().getClass()) {
                return false;
            }

            //only type is comparable can compare
            if(src instanceof Comparable && condition.getTarget() instanceof Comparable) {
                Comparable comp1 = (Comparable)src;
                Comparable comp2 = (Comparable)condition.getTarget();
                int res = comp1.compareTo(comp2);
                if(condition.getOp() == Operator.OP_TYPE_GREATER) {
                    return res > 0;
                } else if(condition.getOp() == Operator.OP_TYPE_LESS) {
                    return res < 0;
                } else if(condition.getOp() == Operator.OP_TYPE_EQUAL) {
                    return res == 0;
                }

            }

        }
        return false;

    }

    @Override
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
            }
            else {
                visitCB.accept(e);
            }
        }
        if(finishCB != null) {
            finishCB.run();
        }
    }

    protected Context context;// = new Context(this);
}
