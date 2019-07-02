package xln.common.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xln.common.web.config.ResultDescribable;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResponse {

    public void addProperty(String key, Object value) {
        if(properties == null) {
            properties = new HashMap<>();
        }
        properties.put(key, value);
    }

    @JsonIgnore
    public void setResult(int resultCode, String description) {
        resultCode = resultCode;
        addProperty("_description", description);
    }

    @JsonIgnore
    public void setResult(ResultDescribable describable) {
        resultCode = describable.getResultCode();
        addProperty("_description", describable.getResultDescription());
    }

    @JsonIgnore
    public String getResult() {
        return (properties != null)?(String)properties.get("_description"):null;

    }

    protected int resultCode;
    protected Map<String, Object> properties;// = new HashMap<String, String>();
}
