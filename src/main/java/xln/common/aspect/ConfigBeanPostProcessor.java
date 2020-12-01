package xln.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import xln.common.annotation.AspectField;
import xln.common.config.EtcdConfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


@Component
@Slf4j
public class ConfigBeanPostProcessor implements BeanPostProcessor {


    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        return bean;
    }

    //Work around to force set through proxy to trigger aspect rightly
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        var annoClasses = new HashSet<Class>();
        getAllAnnotation(bean.getClass(), Configuration.class, annoClasses);
        if(annoClasses.size() > 0) {

            var fields = new ArrayList<Field>();
            getAllFields(bean.getClass(), fields);//it.getClass().getDeclaredFields();
            for(int i = 0; i < fields.size(); i++) {
                var annotations = fields.get(i).getAnnotationsByType(AspectField.class);
                if(annotations != null && annotations.length > 0) {
                    var field = fields.get(i);
                    try {
                        //fields
                        field.setAccessible(true);
                        //var obj = field.get(it);
                        var name = field.getName();

                        var getMethod = bean.getClass().getMethod("get" + StringUtils.capitalize(name));
                        var setMethod = bean.getClass().getMethod("set" + StringUtils.capitalize(name), getMethod.getReturnType());


                        var obj = getMethod.invoke(bean);
                        setMethod.invoke(bean, obj);

                        //}
                    } catch (Exception ex) {
                        log.error("", ex);
                    }
                }
            }

        }


        return bean;
    }

    public static void getAllFields(Class clazz, ArrayList<Field> ret) {

        var parent = clazz.getSuperclass();
        if(parent != null) {
            getAllFields(parent, ret);
        }

        var fields = clazz.getDeclaredFields();
        for(var field : fields) {

/*
            if(!field.getType().isPrimitive() && field.getType().getPackageName().startsWith("xln")) {
                log.info(field.getType().toString());
                getAllFields(field.getType(), ret);
            }

 */
            ret.add(field);
        }

    }


    public static void getAllAnnotation(Class clazz, Class target, Set<Class> annoClass) {

        var annotations = clazz.getAnnotationsByType(target);
        for(var annotation : annotations) {

            annoClass.add(annotation.annotationType());
            return;
        }

        var parent = clazz.getSuperclass();
        if(parent != null) {
            getAllAnnotation(parent, target, annoClass);
        }

        var interfaces = clazz.getInterfaces();
        for(var interf: interfaces) {

            getAllAnnotation(interf, target, annoClass);
        }



    }
}
