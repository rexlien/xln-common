package xln.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

        }catch(Exception ex) {
            log.error("", ex);
            return Mono.empty();
        }
    }

    public static <T> Mono<T> httpCallMono(String url,  Map<String, ?> urlValues, HttpMethod method, Class type, Map<String, String> headers) {
        try {
            if(urlValues == null) {

                return reactiveClient.method(method).uri(url).headers((t)->{
                    for(Map.Entry<String, String> e: headers.entrySet()) {
                        t.add(e.getKey(), e.getValue());
                    }
                }).retrieve().bodyToMono(type);

            }
            return reactiveClient.method(method).uri(url, urlValues).headers((t)->{
                for(Map.Entry<String, String> e: headers.entrySet()) {
                    t.add(e.getKey(), e.getValue());
                }
            }).retrieve().bodyToMono(type);

        }catch(Exception ex) {
            log.error("", ex);
            return Mono.empty();
        }
    }

    public static <T> Flux<T> httpGetFlux(String url, Class type) {
        try {
            return reactiveClient.get().uri(url).retrieve().bodyToFlux(type);

        }catch(Exception ex) {
            log.error("", ex);
            return Flux.empty();
        }
    }



    public static WebClient getWebClient() {
        return reactiveClient;
    }

}