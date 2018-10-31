package xln.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class HttpException extends RuntimeException {

    HttpStatus status;

}
