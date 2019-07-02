package xln.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseException extends RuntimeException{

    int errorCode;
    String errorDescription;

}
