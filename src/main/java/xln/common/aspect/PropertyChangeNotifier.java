package xln.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.stereotype.Service;
import xln.common.annotation.ProxyEndpoint;
import xln.common.config.EtcdConfig;
import xln.common.service.EtcdClient;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PropertyChangeNotifier {


    private ConcurrentHashMap<Class, ConcurrentHashMap<String, Object>> properties;

    public PropertyChangeNotifier(List<PropertyChangeAware> propertyChangeAwareList) {

        propertyChangeAwareList.forEach(it  -> {
            var proxyClass = it.getClass();
            var fields = it.getClass().getSuperclass().getDeclaredFields();
            for(int i = 0; i < fields.length; i++) {
                var annotation = fields[i].getAnnotationsByType(ProxyEndpoint.class);
                if(annotation != null) {
                    try {
                        //fields
                        var field = fields[i].get(it);
                        //if(field instanceof String) {
                            var name = fields[i].getName();
                            var clazz = fields[i].getClass().getCanonicalName();
                            int test = 10;

                        //}
                    }catch(IllegalAccessException ex) {
                        log.error("filed access error");
                    }
                }
            }
        });
    }
}
