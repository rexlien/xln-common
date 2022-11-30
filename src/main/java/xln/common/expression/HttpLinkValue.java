package xln.common.expression;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import xln.common.serializer.ObjectDeserializer;
import xln.common.serializer.ObjectSerializer;

import java.util.HashMap;
import java.util.Map;

public class HttpLinkValue implements Element, Value {

    private String link;
    private Map<String, String> srcHeaders = new HashMap<>();

    @JsonDeserialize(using = ObjectDeserializer.class)
    @JsonSerialize(using = ObjectSerializer.class)
    private Object srcBody = "";

    private String symbol;
    private String tag = "";

    public HttpLinkValue() {

    }

    public HttpLinkValue(String symbol, String link) {
        this.symbol = symbol;
        this.link = link;

    }

    @Override
    public Object eval(Evaluator evaluator) {
        return evaluator.eval(this);
    }

    public String getSrcLink() {
        return link;
    }

    public HttpLinkValue setSrcLink(String link) {
        this.link = link;
        return this;
    }

    public Map<String, String> getSrcHeaders() {
        return srcHeaders;
    }

    public HttpLinkValue setSrcHeaders(Map<String, String> srcHeaders) {
        this.srcHeaders = srcHeaders;
        return this;
    }

    public Object getSrcBody() {
        return srcBody;
    }

    public HttpLinkValue setSrcBody(Object srcBody) {
        this.srcBody = srcBody;
        return this;
    }

    public String getSymbol() {
        return symbol;
    }

    public HttpLinkValue setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    //public Object getCurValue() {
      //  return curValue;
    //}


    public String getTag() {
        return tag;
    }

    public HttpLinkValue setTag(String tag) {
        this.tag = tag;
        return this;
    }
}
