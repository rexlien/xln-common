package xln.common.expression;

public interface Action extends Element {

    Object invoke(Context context);
}
