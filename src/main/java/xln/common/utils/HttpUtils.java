package xln.common.utils;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public class HttpUtils {

    public static final WebClient reactiveClient = WebClient.create();

    public static <T> Mono<T> httpGetMono(String url, Class type) {
        try {
            return reactiveClient.get().uri(url).retrieve().bodyToMono(type);

        } catch (Exception ex) {
            log.error("", ex);
            return Mono.empty();
        }
    }

    public static <T> Mono<T> httpCallMono(String url, Map<String, ?> urlValues, HttpMethod method, Class type,
            Map<String, String> headers) {
        try {

            var methodCall = reactiveClient.method(method);
            if (urlValues == null) {

                return methodCall.uri(url).headers((t) -> {
                    if(headers != null) {
                        for (Map.Entry<String, String> e : headers.entrySet()) {
                            t.add(e.getKey(), e.getValue());
                        }
                    }
                }).retrieve().bodyToMono(type);

            } else {
                return methodCall.uri(url, urlValues).headers((t) -> {
                    if(headers != null) {
                        for (Map.Entry<String, String> e : headers.entrySet()) {
                            t.add(e.getKey(), e.getValue());
                        }
                    }
                }).retrieve().bodyToMono(type);
            }

        } catch (Exception ex) {
            log.error("", ex);
            return Mono.empty();
        }
    }

    public static <T> Mono<T> httpCallMono(String url, Map<String, ?> urlValues, HttpMethod method, Class type,
            Map<String, String> headers, Object body) {
        Gson gson = new Gson();
        return httpCallMono(url, urlValues, method, type, headers, gson.toJson(body));
    }

    public static <T> Mono<ResponseEntity<T>> httpCallMonoResponseEntity(String url, Map<String, ?> urlValues, HttpMethod method, Class type,
                                                                    Map<String, String> headers, Object body) {
        Gson gson = new Gson();
        return httpCallMono(url, urlValues, method, headers,gson.toJson(body)).toEntity(type);
    }

    public static WebClient.ResponseSpec httpCallMono(String url, Map<String, ?> urlValues, HttpMethod method,
                                                      Map<String, String> headers, String body) {
        try {
            log.debug("Call http url:" + url);
            var methodCall = reactiveClient.method(method);
            WebClient.RequestBodySpec requestSpec;
            if (urlValues == null) {
                requestSpec = methodCall.uri(url);
            } else {
                requestSpec = methodCall.uri(url, urlValues);
            }

            requestSpec = requestSpec.headers((t) -> {
                if(headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        t.add(e.getKey(), e.getValue());
                    }
                }
                if (body != null && !body.isEmpty()) {
                    t.add("Content-Type", "application/json");
                }
            });

            if (body != null && !body.isEmpty()) {
                requestSpec.body(BodyInserters.fromValue(body));
            }
            return requestSpec.retrieve();

        } catch (Exception ex) {
            log.error("", ex);
            return null;
        }
    }



    public static <T> Mono<T> httpCallMono(String url, Map<String, ?> urlValues, HttpMethod method, Class type,
            Map<String, String> headers, String body) {

        return httpCallMono(url, urlValues, method, headers, body).bodyToMono(type);
    }

    public static <T> Flux<T> httpGetFlux(String url, Class type) {
        try {
            return reactiveClient.get().uri(url).retrieve().bodyToFlux(type);

        } catch (Exception ex) {
            log.error("", ex);
            return Flux.empty();
        }
    }

    public static WebClient getWebClient() {
        return reactiveClient;
    }

}
