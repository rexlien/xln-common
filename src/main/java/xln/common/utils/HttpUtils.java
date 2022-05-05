package xln.common.utils;

import com.google.gson.Gson;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Slf4j
public class HttpUtils {

    private static final HttpClient client = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(15));

    private static final WebClient reactiveClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(client)).build();

    public static <T> Mono<T> httpGetMono(String url, Class type) {
        try {
            return reactiveClient.get().uri(url).retrieve().bodyToMono(type);

        } catch (Exception ex) {
            log.error("", ex);
            return Mono.empty();
        }
    }

    public static <T> Mono<ResponseEntity<T>> httpGetMonoEntity(String url, Map<String, String> headers, Class type) {
        try {
            return reactiveClient.get().uri(url).headers((t) -> {
                if(headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        t.add(e.getKey(), e.getValue());
                    }
                }
            }).retrieve().toEntity(type);

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

        WebClient.ResponseSpec responseSpec;
        if(body instanceof String) {
            var strBody = (String)body;
            responseSpec = httpCallMono(url, urlValues, method, headers, strBody);
        } else {
            Gson gson = new Gson();
            responseSpec = httpCallMono(url, urlValues, method, headers,gson.toJson(body));
        }

        return (responseSpec != null)?responseSpec.toEntity(type):Mono.empty();
    }

    public static <T> Mono<ResponseEntity<T>> httpCallMonoResponseEntity(String url, Map<String, ?> urlValues, HttpMethod method, ParameterizedTypeReference<T> type,
                                                                         Map<String, String> headers, Object body) {

        WebClient.ResponseSpec responseSpec;
        if(body instanceof String) {
            var strBody = (String)body;
            responseSpec = httpCallMono(url, urlValues, method, headers, strBody);
        } else {
            Gson gson = new Gson();
            responseSpec = httpCallMono(url, urlValues, method, headers,gson.toJson(body));
        }

        return (responseSpec != null)?responseSpec.toEntity(type):Mono.empty();
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
                    //only default to json type when not specified
                    if(!t.containsKey("Content-Type")) {
                        t.add("Content-Type", "application/json");
                    }
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

    public static <T> Mono<T> httpCallMono(String url, Map<String, ?> urlValues, HttpMethod method, ParameterizedTypeReference<T> type,
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

    public static <T> Mono<T> httpRetry(Mono<T> request, int attempt, int backoffTime) {

        return request.retryWhen(Retry.fixedDelay(attempt, Duration.ofMillis(backoffTime)).filter((ex)-> {
            if(ex instanceof WebClientResponseException) {
                WebClientResponseException wbc = (WebClientResponseException)ex;
                if(wbc.getStatusCode().is5xxServerError()) {
                    return true;
                }
            }
            else if(ex instanceof WebClientRequestException) {
                //assuming reqctor-netty only
                if(((WebClientRequestException) ex).getRootCause() instanceof TimeoutException) {
                    return true;
                }
            }
            return false;
        }).doBeforeRetry((e) -> {
            log.error("Do Retry :" + e.totalRetries() + " Reason: " + e.failure().getMessage());
        }));
    }


    public static class RequestBuilder<T> {

        private int attempt = -1;
        private int backoffTime = -1;
        private long timeoutMillis = 0;
        private Mono<T> request;

        public RequestBuilder(Mono<T> request) {
            this.request = request;

        }


        public RequestBuilder<T> setRetry(int attempt, int backoffTime) {
            this.attempt = attempt;
            this.backoffTime = backoffTime;
            return this;
        }

        public RequestBuilder<T> setTimeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Mono<T> build() {
            var newMono = this.request;
            if(timeoutMillis > 0) {
                newMono = newMono.timeout(Duration.ofMillis(timeoutMillis));
            }

            if(attempt > 0 && backoffTime >0) {
                newMono = HttpUtils.httpRetry(request, attempt, backoffTime);
            }
            return newMono;
        }
    }

    public static WebClient getWebClient() {
        return reactiveClient;
    }

}
