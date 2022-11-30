package xln.common.expression;

import lombok.extern.slf4j.Slf4j;
import xln.common.expression.v2.ValueCondition;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ProgressConditionEvaluator extends Evaluator<Result> {

    public ProgressConditionEvaluator(Context context) {
        super(context);
    }

    @Override
    public Result eval(Operator operator) {
        if (operator.left == null && operator.right == null) {
            return new Result(true);
        } else if (operator.left != null && operator.right != null) {

            Result left = (Result) operator.left.eval(this);
            Result right = (Result) operator.right.eval(this);

            if (operator.op == Const.OP_TYPE_AND) {

                Result result = new Result(left.getResult() && right.getResult());
                result.subResult(left);
                result.subResult(right);
                return result;

            } else {
                Result result = new Result(left.getResult() || right.getResult());
                result.subResult(left);
                result.subResult(right);
                return result;
            }


        } else if (operator.left != null) {

            Result left = (Result) operator.left.eval(this);
            Result result = new Result(left.getResult());
            result.subResult(left);

            return result;
        } else {

            Result right = (Result) operator.right.eval(this);
            Result result = new Result(right.getResult());
            result.subResult(right);
            return result;
        }


    }

    @Override
    public Result eval(LogicalOperator operator) {
        if (operator.getOp() != Const.OP_TYPE_AND && operator.getOp() != Const.OP_TYPE_OR) {
            log.error("Operator type can only be OR or AND");
            return new Result(false);
        }
        if (operator.getElements() == null || operator.getElements().isEmpty()) {
            return new Result(true);
        }
        //Boolean ret = null;
        Result ret = null;
        for (Element elem : operator.getElements()) {


            Result child = (Result) elem.eval(this);
            if (ret == null) {

                ret = new Result(child.getResult());
                ret.subResult(child);

            } else {
                if (operator.getOp() == Const.OP_TYPE_AND) {
                    ret.setResult(ret.getResult() && child.getResult());
                } else {
                    ret.setResult(ret.getResult() || child.getResult());
                }
                ret.subResult(child);
            }

        }
        return ret;
    }

    @Override
    public Result eval(Condition condition) {

        Object src = null;
        try {
            src = context.getSource(condition.getSrcPath(), condition.getSrcHeaders(), condition.getSrcBody()).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("could not get context source", e);
        }
        if (src != null) {
            if (condition.getOp() == Const.OP_TYPE_CONTAINS) {

                if (src instanceof Map) {
                    Map map = (Map) src;
                    return new Result(map.containsKey(condition.getTarget()), new Result.Progress(map, condition.getTarget())).setTag(condition.getTag());
                } else {
                    log.error("Only map src can check contains condition");
                    //return src in progress?
                    return new Result(false, new Result.Progress(src, condition.getTarget())).setTag(condition.getTag());
                }
            } else {

                var target = condition.getTarget();
                //only same type and string/numbers are comparable
                if (src.getClass() != target.getClass()) {

                    if (!(src instanceof Number)) {
                        return new Result(false).setTag(condition.getTag());
                    }
                    if (!(target instanceof Number || target instanceof String)) {
                        return new Result(false).setTag(condition.getTag());
                    }
                    //special case for numbers
                    target = Utils.castNumber(src.getClass(), target);
                    if (target == null) {
                        return new Result(false).setTag(condition.getTag());
                    }
                }

                //only type is comparable can compare
                if (src instanceof Comparable && target instanceof Comparable) {
                    Comparable comp1 = (Comparable) src;
                    Comparable comp2 = (Comparable) target;

                    var progress = new Result.Progress(src, target);
                    int res = comp1.compareTo(comp2);
                    if (condition.getOp() == Const.OP_TYPE_GREATER) {
                        return new Result(res > 0, progress).setTag(condition.getTag());
                    } else if (condition.getOp() == Const.OP_TYPE_LESS) {
                        return new Result(res < 0, progress).setTag(condition.getTag());
                    } else if (condition.getOp() == Const.OP_TYPE_EQUAL) {
                        return new Result(res == 0, progress).setTag(condition.getTag());
                    } else if (condition.getOp() == Const.OP_TYPE_GREATER_OR_EQUAL) {
                        return new Result((res >= 0), progress).setTag(condition.getTag());
                    } else if (condition.getOp() == Const.OP_TYPE_LESS_OR_EQUAL) {
                        return new Result((res <= 0), progress).setTag(condition.getTag());
                    }

                }
            }

        }
        //even source is null, target still returns in progress
        return new Result(false,  new Result.Progress(null, condition.getTarget())).setTag(condition.getTag());
    }

    @Override
    public Result eval(Element element) {

        if(element instanceof ValueCondition) {

            ValueCondition condition = (ValueCondition) element;
            Object src = null;
            Result retResult = new Result();
            try {
                var srcValue = condition.getSrcValue();
                if (srcValue instanceof HttpLinkValue) {
                    HttpLinkValue linkValue = (HttpLinkValue)srcValue;
                    src = context.getSource(linkValue.getSrcLink(), linkValue.getSrcHeaders(), linkValue.getSrcBody()).get(10, TimeUnit.SECONDS);
                } else if(srcValue instanceof LogicalOperator) {
                    var result = (Result)srcValue.eval(this);
                    retResult.subResult(result);
                    src = result.getResult();
                }
            }
            catch(Exception e){
                log.error("could not get context source", e);
            }

            if (src != null) {
                if (condition.getOp() == Const.OP_TYPE_CONTAINS) {

                    if (src instanceof Map) {
                        Map map = (Map) src;
                        return retResult.setResult(map.containsKey(condition.getTargetValue())).setProgress(new Result.Progress(map, condition.getTargetValue())).setTag(condition.getTag());
                    } else {
                        log.error("Only map src can check contains condition");
                        //return src in progress?
                        return retResult.setResult(false).setProgress(new Result.Progress(src, condition.getTargetValue())).setTag(condition.getTag());
                    }
                } else {

                    var target = condition.getTargetValue();
                    //only same type and string/numbers are comparable
                    if (src.getClass() != target.getClass()) {

                        if (!(src instanceof Number)) {
                            return retResult.setResult(false).setTag(condition.getTag());
                        }
                        if (!(target instanceof Number || target instanceof String)) {
                            return retResult.setResult(false).setTag(condition.getTag());
                        }
                        //special case for numbers
                        target = Utils.castNumber(src.getClass(), target);
                        if (target == null) {
                            return retResult.setResult(false).setTag(condition.getTag());
                        }
                    }

                    //only type is comparable can compare
                    if (src instanceof Comparable && target instanceof Comparable) {
                        Comparable comp1 = (Comparable) src;
                        Comparable comp2 = (Comparable) target;

                        var progress = new Result.Progress(src, target);
                        int res = comp1.compareTo(comp2);
                        if (condition.getOp() == Const.OP_TYPE_GREATER) {
                            return retResult.setResult(res > 0).setProgress(progress).setTag(condition.getTag());
                        } else if (condition.getOp() == Const.OP_TYPE_LESS) {
                            return retResult.setResult(res < 0).setProgress(progress).setTag(condition.getTag());
                        } else if (condition.getOp() == Const.OP_TYPE_EQUAL) {
                            return retResult.setResult(res == 0).setProgress(progress).setTag(condition.getTag());
                        } else if (condition.getOp() == Const.OP_TYPE_GREATER_OR_EQUAL) {
                            return retResult.setResult(res >= 0).setProgress(progress).setTag(condition.getTag());
                        } else if (condition.getOp() == Const.OP_TYPE_LESS_OR_EQUAL) {
                            return retResult.setResult(res <= 0).setProgress(progress).setTag(condition.getTag());
                        }

                    }
                }

            }
            //even source is null, target still returns in progress
            return new Result(false,  new Result.Progress(null, condition.getTargetValue())).setTag(condition.getTag());
        }

        return null;
    }
}
