package xln.common.log;

import com.google.gson.Gson;
import com.google.protobuf.Message;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import xln.common.utils.ProtoUtils;

import java.util.HashMap;
import java.util.Map;

import static net.logstash.logback.marker.Markers.*;

@Slf4j
public class LogStash {

    public final static String KEY_ES_INDEX = "@es_index";
    public final static String KEY_MSG_BODY = "body";

    private static final Marker metrics = MarkerFactory.getMarker("metrics-release");

    //log default by jackson
    public static void esLog(String index, Object payload) {


        var fields = new HashMap<String, Object>();
        fields.put(KEY_MSG_BODY, payload);
        fields.put(KEY_ES_INDEX, index);

        log.trace(appendEntries(fields).and(metrics), "");
    }

    //log raw by gson
    public static void esLogRaw(String index, Object payload) {

        Gson gson = new Gson();
        log.trace(append(KEY_ES_INDEX, index).and(appendRaw(KEY_MSG_BODY, gson.toJson(payload))).
                and(metrics), "");

    }

    //log proto message
    public static void esLogProto(String index, Message message) {

        var body = ProtoUtils.json(message);
        log.trace(append(KEY_ES_INDEX, index).and(appendRaw(KEY_MSG_BODY, body).and(metrics)), "");
    }





}
