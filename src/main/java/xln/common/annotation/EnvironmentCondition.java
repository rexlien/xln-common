package xln.common.annotation;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

public class EnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

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
