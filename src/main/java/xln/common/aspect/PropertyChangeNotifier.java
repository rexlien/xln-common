package xln.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import xln.common.annotation.AspectField;
import xln.common.annotation.AspectGetter;
import xln.common.annotation.AspectSetter;
import xln.common.aspect.PropertyChangeAware;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service(value="xln-property-change-notifier")
//@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class PropertyChangeNotifier {


    private ConcurrentHashMap<Class, ConcurrentHashMap<String, Object>> properties;
/*
    public PropertyChangeNotifier(List<PropertyChangeAware> propertyChangeAwareList) {

        propertyChangeAwareList.forEach(it  -> {
            var proxyClass = it.getClass();

            var fields = new ArrayList<Field>();
            getAllFields(it.getClass(), fields);//it.getClass().getDeclaredFields();
            for(int i = 0; i < fields.size(); i++) {
                var annotations = fields.get(i).getAnnotationsByType(AspectField.class);
                if(annotations != null && annotations.length > 0) {
                    var field = fields.get(i);
                    try {
                        //fields

                        field.setAccessible(true);
                        var obj = field.get(it);

                        //if(field instanceof String) {
                        var name = field.getName();
                        var clazz = field.getClass().getCanonicalName();



                        //}
                    }catch(IllegalAccessException ex) {
                        log.error("filed access error");
                    }catch (Exception ex) {
                        log.error("", ex);
                    }
                }
            }

            var methods = it.getClass().getDeclaredMethods();

            for(var method: methods) {

                var getAnnotations = method.getAnnotationsByType(AspectGetter.class);
                var setAnnotations = method.getAnnotationsByType(AspectSetter.class);

                if(getAnnotations.length != 0 && setAnnotations.length != 0) {


                    //me

                }

            }

        });
    }

    public static void getAllFields(Class clazz, ArrayList<Field> ret) {

        var parent = clazz.getSuperclass();
        if(parent != null) {
            getAllFields(parent, ret);
        }

        var fields = clazz.getDeclaredFields();
        for(var field : fields) {

            ret.add(field);
        }

    }

 */
}
