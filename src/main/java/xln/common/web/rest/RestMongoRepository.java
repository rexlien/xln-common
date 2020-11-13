package xln.common.web.rest;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface RestMongoRepository<T> extends ReactiveMongoRepository<T, ObjectId> {

    Flux<T> findAllBy(Pageable pageable);
}
