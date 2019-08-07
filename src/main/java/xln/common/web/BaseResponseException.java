package xln.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseException extends RuntimeException{

    public BaseResponseException(ResultDescribable resultDescribable) {
        errorCode = resultDescribable.getResultCode();
        errorDescription = resultDescribable.getResultDescription();
    }
    private int errorCode;
    private String errorDescription;

}
