package xln.common.web;

import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.Map;

@Component
public class DefaultErrorAttribute extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, boolean includeStackTrace) {
        Map<String, Object> attributes = super.getErrorAttributes(request, includeStackTrace);
        attributes.remove("message");
        attributes.remove("requestId");
        attributes.remove("path");
        return attributes;
    }
}
