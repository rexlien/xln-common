package xln.common.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;

import javax.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
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

        var httpBody = ex.getHttpBody();

        if(httpBody == null) {
            httpBody = ex.getStatus().toString();
        }

        var logBody = ex.getLogBody();
        if(logBody == null) {
            logBody = ex.getHttpBody();
            if(logBody == null) {
                logBody = ex.getStatus().toString();
            }
        }
        if(ex.getLogLevel() == HttpException.LogLevel.LL_ERROR) {
            //only log stack trace when error
            log.error(logBody, ex);
        } else if(ex.getLogLevel() == HttpException.LogLevel.LL_INFO) {
            log.info(logBody);
        } else {
            log.warn(logBody);
        }
        return new ResponseEntity(httpBody, ex.getStatus());


    }

    @ExceptionHandler(BaseResponseException.class)
    public ResponseEntity handleBaseResponseException(BaseResponseException ex) {

        BaseResponse response = new BaseResponse();
        response.setResult(ex.getErrorCode(), ex.getErrorDescription());
        return new ResponseEntity(response, HttpStatus.OK);

    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity handleAllRequestException(Exception ex) {

        log.error("handleAllRequestException", ex);
        return new ResponseEntity("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity handleException(ConstraintViolationException ex) {

        log.warn("contraint Violation", ex);
        BaseResponse response = new BaseResponse();
        response.setResult(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity(response, HttpStatus.BAD_REQUEST);
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
