package xln.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class XLNTurboFilter extends ch.qos.logback.classic.turbo.TurboFilter {


    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {

        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        if(marker instanceof LogstashMarker) {
            return FilterReply.ACCEPT;
        }
        else {
            return FilterReply.NEUTRAL;
        }
    }

}
