package xln.common.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class, MongoReactiveAutoConfiguration.class,
        QuartzAutoConfiguration.class})
@ComponentScan
@ComponentScan(basePackages={"xln.common"})
public class XLNApplication {

    public static void main(String[] args) {

        SpringApplication.run(XLNApplication.class, args);

    }
}
