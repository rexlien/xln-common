package xln.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
