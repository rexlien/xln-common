package xln.common.cache;

import reactor.core.publisher.Mono;

public interface ReactiveCache {

    Mono<Object> putReactive(Object key, Object value);
}
