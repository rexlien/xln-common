package xln.common.expression;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import xln.common.serializer.ObjectDeserializer;
import xln.common.serializer.ObjectSerializer;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Condition implements Element{
    private String srcPath;
    private int op;
    private Object target;
    private String tag;
    private Map<String, String> srcHeaders = new HashMap<>();


    @JsonDeserialize(using = ObjectDeserializer.class)
    @JsonSerialize(using = ObjectSerializer.class)
    private Object srcBody = "";
/*
    public Condition setSrcBody(String srcBody) {
        this.srcBody = srcBody;

        Gson gson = new Gson();
        Class<Map<Object, Object>> mapClazz =
                (Class<Map<Object,Object>>)(Class)Map.class;
        cachedSrcMap = gson.fromJson(srcBody, mapClazz);

        return this;
    }


    @JsonIgnore
    public Map<Object, Object> getCachedSrcMap() {
        return cachedSrcMap;
    }

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    @Transient
    private Map<Object, Object> cachedSrcMap = new HashMap<>();
*/
    public Condition(String srcPath, int op, Object target, String tag) {
        this(srcPath, op, target);
        this.tag = tag;
    }

    public Condition(String srcPath, int op, Object target) {
        this.srcPath = srcPath;
        this.op = op;
        this.target = target;
    }

    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }


}
