package xln.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

public class HttpException extends RuntimeException {

    public HttpException(HttpStatus status) {
        this.status = status;
    }
    public HttpException(HttpStatus status, String httpBody) {
        this.httpBody = httpBody;
        this.status = status;
    }

    private HttpStatus status;

    //http response will output httpBody if it's not null
    private String httpBody;

    //log will output logBody if logBody is not null
    private String logBody;

    //default to log in error level
    private Boolean errorLevel = true;

    public HttpStatus getStatus() {
        return status;
    }

    public HttpException setStatus(HttpStatus status) {
        this.status = status;
        return this;
    }

    public String getHttpBody() {
        return httpBody;
    }

    public HttpException setHttpBody(String httpBody) {
        this.httpBody = httpBody;
        return this;
    }

    public String getLogBody() {
        return logBody;
    }

    public HttpException setLogBody(String logBody) {
        this.logBody = logBody;
        return this;
    }

    public Boolean getErrorLevel() {
        return errorLevel;
    }

    public HttpException setErrorLevel(Boolean errorLevel) {
        this.errorLevel = errorLevel;
        return this;
    }
}
