package xln.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = { MongoReactiveAutoConfiguration.class, MongoAutoConfiguration.class, GsonAutoConfiguration.class,
        MongoDataAutoConfiguration.class, QuartzAutoConfiguration.class, MongoReactiveRepositoriesAutoConfiguration.class})
@Slf4j
@ComponentScan(basePackages = { "xln.common"})
public class SpringSparkMain {

    public static volatile ConfigurableApplicationContext context = null;

    public static void main(String[] args) {
        
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SpringSparkMain.class);
        builder.web(WebApplicationType.NONE);
        SpringApplication spring = builder.build();

        context = spring.run();

    }
}
