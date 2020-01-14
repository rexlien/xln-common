package xln.common.web.config;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.schema.ModelPropertyBuilderPlugin;
import springfox.documentation.spi.schema.contexts.ModelPropertyContext;
import springfox.documentation.spi.service.ParameterBuilderPlugin;
import springfox.documentation.spi.service.contexts.ParameterContext;
import springfox.documentation.swagger.common.SwaggerPluginSupport;
import xln.common.web.ResultDescribable;

import java.util.*;
import java.lang.reflect.Field;

@Component
@Slf4j
@Order(SwaggerPluginSupport.SWAGGER_PLUGIN_ORDER + 1000)
public class SwaggerPlugin implements ModelPropertyBuilderPlugin, ParameterBuilderPlugin {
    @Override
    public void apply(ModelPropertyContext context) {
        try {
            Optional<BeanPropertyDefinition> beanDef = context.getBeanPropertyDefinition();
            if (beanDef.isPresent()) {
                AnnotatedField aField = beanDef.get().getField();
                //log.info(aField.getFullName());
                if (aField != null) {
                    Field field = aField.getAnnotated();
                    if (field != null) {

                        var property = field.getAnnotation(SwaggerResultDescribable.class);
                        if (property != null) {

                            String result = "";
                            for (var clazz: property.clazzDescribable()) {

                                if (clazz != null) {
                                    String desc = buildDescription(context, property, clazz);
                                    if(desc != null) {
                                        result = result.concat(desc);
                                    }
                                }

                            }
                            setDescription(context, property, result);
                        }
                    }
                }

                var accessor =  beanDef.get().getAccessor();
                if(accessor != null) {
                    var anotated = accessor.getAnnotated();
                    if(anotated != null) {
                        var property = anotated.getAnnotation(SwaggerResultDescribable.class);
                        if(property != null) {

                            String result = "";
                            for (var clazz: property.clazzDescribable()) {

                                if (clazz != null) {
                                    String desc = buildDescription(context, property, clazz);
                                    if(desc != null) {
                                        result = result.concat(desc);
                                    }
                                }
                            }
                            setDescription(context, property, result);
                        }
                    }
                }

            }
        } catch (Throwable t) {

            throw new RuntimeException(t);
        }
    }

    static String createMarkdownDescription(Class<? extends Enum<?>> clazz) {
        List<String> lines = new ArrayList<>();

        for (Enum<?> enumVal : clazz.getEnumConstants()) {
            if(enumVal instanceof ResultDescribable) {
                ResultDescribable resultDescribable = (ResultDescribable)enumVal;
                String line = "Code : " + resultDescribable.getResultCode() + " - Description: " + resultDescribable.getResultDescription();
                lines.add(line);
            }
            /*
            else {
                String desc = enumVal.name();//readApiDescription(enumVal);
                if (desc != null) {
                    foundAny = true;
                }
                String line = "* " + enumVal.name() + ": " + (desc == null ? "_@ApiEnum annotation not available_" : desc);
                lines.add(line);
            }
            */

        }

        if(!lines.isEmpty())
            return StringUtils.join(lines, "\n");
        else
            return null;
    }

    private String buildDescription(ModelPropertyContext context, SwaggerResultDescribable property, Class<?> clazz) {
        if (clazz.isEnum()) {
            return createMarkdownDescription((Class<? extends Enum<?>>) clazz) + "\n";
        } else {
            return null;
        }
    }

    private void setDescription(ModelPropertyContext context, SwaggerResultDescribable property, String result) {

        String description = property.value();
        description += "\n" + result;
        context.getBuilder().description(description);
    }


    @Override
    public void apply(ParameterContext context) {

    }

    @Override
    public boolean supports(DocumentationType delimiter) {
        return SwaggerPluginSupport.pluginDoesApply(delimiter);
    }
}
