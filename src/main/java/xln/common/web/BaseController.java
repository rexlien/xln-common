package xln.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;

@RestController
public class BaseController
{
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity handleException(ServerWebInputException ex) {

        //ex.getBindingResult().getFieldError();
        //List<Response> res = new ArrayList<Response>();
        return new ResponseEntity("Bad request", HttpStatus.BAD_REQUEST);
    }
}
