package xln.common.cache;

import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheExpressionEvaluator extends CachedExpressionEvaluator {

    private final Map<ExpressionKey, Expression> keyCache = new ConcurrentHashMap<>(64);


    public EvaluationContext createContext( Object target, Method method, Object[] args) {
        return new MethodBasedEvaluationContext(target, method, args, this.getParameterNameDiscoverer());
    }


    public Object evaluate(String keyExpression, AnnotatedElementKey anotatedKey, EvaluationContext context) {
        return getExpression(keyCache, anotatedKey, keyExpression).getValue(context);
    }
}
