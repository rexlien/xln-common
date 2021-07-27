package xln.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;


public class SparkMain {

    public static <T> ConfigurableApplicationContext runMain(String[] args, Class<T> appClass) {

        SpringApplicationBuilder builder = new SpringApplicationBuilder(appClass);
        String isWeb = System.getenv("XLN_SPARK_WEB");
        String runnerClass = System.getenv("XLN_SPARK_RUNNER");
        if(isWeb != null && isWeb.equals("true")) {
            builder.web(WebApplicationType.REACTIVE);
        } else {
            builder.web(WebApplicationType.NONE);
        }
        builder.registerShutdownHook(true);
        SpringApplication spring = builder.build();
        ConfigurableApplicationContext context = spring.run();
        if(isWeb == null) {
            if (runnerClass != null) {
                try {
                    Class c = Class.forName(runnerClass);
                    Object interfaceType = (Object) c.newInstance();
                    if (interfaceType instanceof SparkRunner) {
                        SparkRunner sparkRunner = (SparkRunner) interfaceType;
                        sparkRunner.run(context);//ClassTag.apply(ScalaSparkApp.class));
                    }

                } catch (Exception ex) {
                    System.out.println(ex.toString());
                }
                //close context since it's a job
                context.close();
                context = null;
            } else {
                System.out.println("no runner class");
                context.close();

            }
        }
        SparkExecutorApp.context = context;
        return context;
    }

    public static <T> ConfigurableApplicationContext executorRunMain(String[] args, Class<T> appClass) {

        SpringApplicationBuilder builder = new SpringApplicationBuilder(appClass);
        String isWeb = System.getenv("XLN_SPARK_WEB");
        if(isWeb != null && isWeb.equals("true")) {
            builder.web(WebApplicationType.REACTIVE);
        } else {
            builder.web(WebApplicationType.NONE);
        }
        builder.registerShutdownHook(true);
        SpringApplication spring = builder.build();
        ConfigurableApplicationContext context = spring.run();

        SparkExecutorApp.context = context;
        return context;
    }
}