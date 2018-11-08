package xln.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    protected int resultCode;
    protected Map<String, Object> properties;// = new HashMap<String, String>();
}
