package xln.common;


import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import xln.common.config.ServiceConfig;

import javax.annotation.PostConstruct;
import java.util.List;


@Configuration
@Slf4j
public class Context {

    @Autowired
    private GenericApplicationContext context;

    @Autowired
    private ServiceConfig serviceConfig;

    private static Logger logger = LoggerFactory.getLogger(Context.class);

    @PostConstruct
    void postConstruct()
    {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(context.getClassLoader());
        try {

            List<String> paths = serviceConfig.getResourcePath();
            for(String resPath : paths) {
                Resource[] resources = resolver.getResources(resPath);
                for (Resource res : resources) {
                    XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
                    reader.loadBeanDefinitions(res);

                }
            }
        }catch(Exception e) {
            logger.error("Resource Load failed");
        }




    }

}
