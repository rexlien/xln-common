package xln.common.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xln.common.web.config.SwaggerResultDescribable;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Slf4j
public class BaseResponse {

    public static class BaseResult implements  ResultDescribable {

        public static final BaseResult SUCCEEDED = new BaseResult(0, "Succeeded");
        public static final BaseResult ERROR_INTERNAL_ERROR = new BaseResult(-1, "Internal Error");

        public BaseResult(int code, final String desc) {
            this.code = code;
            this.desc = desc;
        }

        private int code;
        private String desc;

        @Override
        public int getResultCode() {
            return code;
        }

        @Override
        public String getResultDescription() {
            return desc;
        }
    }

/*
    public enum BaseResultParams implements ResultDescribable {

        SUCCEEDED(0, "Succeeded"),
        ERROR_INTERNAL_ERROR(-1, "Internal Error");


        BaseResultParams(int code, final String desc) {
            this.code = code;
            this.desc = desc;
        }

        private int code;
        private String desc;

        @Override
        public int getResultCode() {
            return code;
        }

        @Override
        public String getResultDescription() {
            return desc;
        }
    }
*/

    public BaseResponse(ResultDescribable describable) {
        this.setResult(describable);
    }
    public BaseResponse() {}

    public BaseResponse addProperty(String key, Object value) {
        if(properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
        return this;
    }

    @JsonIgnore
    public BaseResponse setResult(int resultCode, String description) {
        this.resultCode = resultCode;
        addProperty("_description", description);
        return this;
    }

    @JsonIgnore
    public BaseResponse setResult(ResultDescribable describable) {
        resultCode = describable.getResultCode();
        addProperty("_description", describable.getResultDescription());
        return this;
    }

    @JsonIgnore
    public String getResult() {
        return (properties != null)?(String)properties.get("_description"):null;

    }

    public int getResultCode() {
        return resultCode;
    }

    public BaseResponse setResultCode(int resultCode) {
        this.resultCode = resultCode;
        return this;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    protected int resultCode;
    protected Map<String, Object> properties;// = new HashMap<String, String>();

    public static <T extends BaseResponse> T of(Class<T> clazz, ResultDescribable describable) {
        try {
            Constructor<?> ctor = clazz.getConstructor();
            BaseResponse response = (BaseResponse)ctor.newInstance();
            response.setResult(describable);
            return (T)(response);
        }catch (Exception ex) {
            log.error("", ex);
            return null;
        }
    }

    public static BaseResponse of(ResultDescribable describable) {
        return new BaseResponse(describable);
    }

}
