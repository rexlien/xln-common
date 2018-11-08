package xln.common.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RestController
public class BaseController
{
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity handleException(ServerWebInputException ex) {

        //ex.getBindingResult().getFieldError();
        //List<Response> res = new ArrayList<Response>();
        return new ResponseEntity("Bad request", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpException.class)
    public ResponseEntity handleAPIRequestException(HttpException ex) {

        return new ResponseEntity(ex.getStatus());
    }


    protected void addCookie(ServerHttpResponse response, String key, String value, int seconds) {
        response.addCookie(ResponseCookie.from(key, value).
                maxAge(Duration.ofSeconds(seconds)).build());

    }

    protected  void enableCache(ServerHttpResponse response, int seconds) {
        String headerValue = CacheControl.maxAge(seconds, TimeUnit.SECONDS).getHeaderValue();
        response.getHeaders().add("Cache-Control", headerValue);
    }

}
