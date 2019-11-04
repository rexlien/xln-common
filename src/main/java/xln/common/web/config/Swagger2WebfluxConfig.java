package xln.common.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebFlux;
import xln.common.annotation.Swagger2Controller;
//import springfox.documentation.swagger2.annotations.EnableSwagger2WebFlux;

@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.REACTIVE)
@Configuration
@EnableSwagger2WebFlux
@Profile({"xln-swagger2-webflux"})
public class Swagger2WebfluxConfig {// implements WebFluxConfigurer {

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





}