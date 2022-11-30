package xln.common.expression;

import lombok.extern.slf4j.Slf4j;
import xln.common.expression.v2.ValueCondition;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConditionEvaluator extends Evaluator<Object>{


    public ConditionEvaluator(Context context) {
        super(context);
    }

    //implementation assuming all boolean
    @Override
    public Object eval(Operator operator) {
        if(operator.left == null && operator.right == null) {
            return true;
        }
        else if(operator.left != null && operator.right != null) {
            if(operator.op == Const.OP_TYPE_AND) {
                return (boolean) operator.left.eval(this) && (boolean) operator.right.eval(this);
            } else {
                return (boolean) operator.left.eval(this) || (boolean) operator.right.eval(this);
            }
        }
        else if(operator.left != null) {
            return (Boolean)operator.left.eval(this);
        }
        else {
            return (Boolean)operator.right.eval(this);
        }
    }

    @Override
    public Object eval(LogicalOperator operator) {
        if(operator.getOp() != Const.OP_TYPE_AND && operator.getOp() != Const.OP_TYPE_OR) {
            log.error("Operator type can only be OR or AND");
            return false;
        }
        if(operator.getElements() == null || operator.getElements().isEmpty()) {
            return true;
        }
        Boolean ret = null;
        for(Element elem : operator.getElements()) {

            //if(elem instanceof Condition || elem instanceof Operator || elem instanceof LogicalOperator)  {
                if (ret == null) {
                    ret = (boolean) elem.eval(this);
                } else {
                    if (operator.getOp() == Const.OP_TYPE_AND) {
                        ret &= (boolean) elem.eval(this);
                    } else {
                        ret |= (boolean) elem.eval(this);
                    }
                }
            //} else {
             //   log.error("element must be Condition or Operator");
             //   return false;
            //}
        }
        return ret;
    }


    public Object eval(Condition condition) {
        Object src = null;
        try {
            src = context.getSource(condition.getSrcPath(), condition.getSrcHeaders(), condition.getSrcBody()).get(10, TimeUnit.SECONDS);
        }catch (Exception e) {
            log.error("could not get context source", e);
        }
        if(src != null) {
            if(condition.getOp() == Const.OP_TYPE_CONTAINS) {

                if(src instanceof Map) {
                    Map map = (Map)src;
                    return map.containsKey(condition.getTarget());
                } else {
                    log.error("Only map src can check contains condition");
                    return false;
                }
            } else {

                var target = condition.getTarget();
                //only same type and string/numbers are comparable
                if (src.getClass() != target.getClass()) {

                    if(!(src instanceof Number)) {
                        return false;
                    }
                    if(!(target instanceof Number || target instanceof String)) {
                        return false;
                    }
                    //special case for numbers
                    target = Utils.castNumber(src.getClass(), target);
                    if(target == null) {
                        return false;
                    }
                }

                //only type is comparable can compare
                if (src instanceof Comparable && target instanceof Comparable) {
                    Comparable comp1 = (Comparable) src;
                    Comparable comp2 = (Comparable) target;
                    int res = comp1.compareTo(comp2);
                    if (condition.getOp() == Const.OP_TYPE_GREATER) {
                        return res > 0;
                    } else if (condition.getOp() == Const.OP_TYPE_LESS) {
                        return res < 0;
                    } else if (condition.getOp() == Const.OP_TYPE_EQUAL) {
                        return res == 0;
                    } else if (condition.getOp() == Const.OP_TYPE_GREATER_OR_EQUAL) {
                        return (res >= 0);
                    } else if(condition.getOp() == Const.OP_TYPE_LESS_OR_EQUAL) {
                        return (res <= 0);
                    }

                }
            }

        }
        return false;

    }

    @Override
    public Object eval(Element element) {

        if(element instanceof ValueCondition) {
            ValueCondition valueCondition = (ValueCondition) element;
            Object src = null;
            try {
                var srcValue = valueCondition.getSrcValue();
                if (srcValue instanceof HttpLinkValue) {
                    HttpLinkValue linkValue = (HttpLinkValue)srcValue;
                    src = context.getSource(linkValue.getSrcLink(), linkValue.getSrcHeaders(), linkValue.getSrcBody()).get(10, TimeUnit.SECONDS);
                } else if(srcValue instanceof LogicalOperator) {
                    src = srcValue.eval(this);
                }
            }
            catch(Exception e){
                log.error("could not get context source", e);
            }

            if(src != null) {
                if(valueCondition.getOp() == Const.OP_TYPE_CONTAINS) {

                    if(src instanceof Map) {
                        Map map = (Map)src;
                        return map.containsKey(valueCondition.getTargetValue());
                    } else {
                        log.error("Only map src can check contains condition");
                        return false;
                    }
                } else {

                    var target = valueCondition.getTargetValue();
                    //only same type and string/numbers are comparable
                    if (src.getClass() != target.getClass()) {

                        if(!(src instanceof Number)) {
                            return false;
                        }
                        if(!(target instanceof Number || target instanceof String)) {
                            return false;
                        }
                        //special case for numbers
                        target = Utils.castNumber(src.getClass(), target);
                        if(target == null) {
                            return false;
                        }
                    }

                    //only type is comparable can compare
                    if (src instanceof Comparable && target instanceof Comparable) {
                        Comparable comp1 = (Comparable) src;
                        Comparable comp2 = (Comparable) target;
                        int res = comp1.compareTo(comp2);
                        if (valueCondition.getOp() == Const.OP_TYPE_GREATER) {
                            return res > 0;
                        } else if (valueCondition.getOp() == Const.OP_TYPE_LESS) {
                            return res < 0;
                        } else if (valueCondition.getOp() == Const.OP_TYPE_EQUAL) {
                            return res == 0;
                        } else if (valueCondition.getOp() == Const.OP_TYPE_GREATER_OR_EQUAL) {
                            return (res >= 0);
                        } else if(valueCondition.getOp() == Const.OP_TYPE_LESS_OR_EQUAL) {
                            return (res <= 0);
                        }
                    }
                }
            }

        }

        return null;
    }


}
