package xln.common.web;

import org.springframework.http.HttpStatus;

public class HttpException extends RuntimeException {

    public enum LogLevel {
        LL_INFO,
        LL_WARN,
        LL_ERROR
    }

    public HttpException(HttpStatus status) {
        super(status.toString());
        this.status = status;
    }
    public HttpException(HttpStatus status, String httpBody) {
        super(httpBody);
        this.httpBody = httpBody;
        this.logBody = httpBody;
        this.status = status;
    }

    public HttpException(HttpStatus status, String httpBody, String logBody) {
        this(status, httpBody);
        this.logBody = logBody;
    }

    public HttpException(HttpStatus status, String httpBody, String logBody, LogLevel logLevel) {
        this(status, httpBody, logBody);
        this.logLevel = logLevel;
    }

    private HttpStatus status;

    //http response will output httpBody if it's not null
    private String httpBody;

    //log will output logBody if logBody is not null
    private String logBody;

    //default to log in error level
    private LogLevel logLevel = LogLevel.LL_INFO;

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

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public HttpException setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }
}
