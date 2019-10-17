package xln.common.annotation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;
import org.springframework.stereotype.Component;
import xln.common.Context;

@Component
public class EnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

        String enable = System.getenv("XLN_ENVCOND_ENABLE");
        if(enable == null || !enable.equals("true")) {
            return true;
        }

        if(metadata instanceof AnnotationMetadataReadingVisitor) {

            AnnotationMetadataReadingVisitor visitor = (AnnotationMetadataReadingVisitor)metadata;
            String clazzName = visitor.getClassName();
            String env = System.getenv(clazzName);
            if(env != null && env.equals("true")) {
                return true;
            }
            return false;
        }

        return true;
    }
}
