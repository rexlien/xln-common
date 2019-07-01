package xln.common.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import xln.common.annotation.Swagger2Controller;


@Configuration
@EnableSwagger2
@Profile({"xln-swagger2-webmvc"})
public class Swagger2WebmvcConfig  {

    @Value("${xln.swagger2.pathFilter:}")
    private String pathFilter;

    @Value("${xln.swagger2.enableSwagger2Controller:false}")
    private Boolean enableSwagger2Controller;

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(enableSwagger2Controller?RequestHandlerSelectors.withClassAnnotation(Swagger2Controller.class):RequestHandlerSelectors.any())
                .paths(pathFilter.isEmpty()?PathSelectors.any():PathSelectors.ant(pathFilter))
                .build();


    }

/*
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //swagger resource
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        //registry.addResourceHandler("/swagger/**").addResourceLocations("classpath:/static/swagger/");
        //registry.addResourceHandler("/swagger/**").addResourceLocations("classpath:/static/swagger/");
    }

*/
}
