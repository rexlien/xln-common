package xln.common.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import net.logstash.logback.marker.LogstashMarker;

public class XLNFilter extends ThresholdFilter {

    @Override
    public FilterReply decide(ILoggingEvent event) {

        if(event.getMarker() instanceof LogstashMarker) {
            return FilterReply.ACCEPT;
        }
        return super.decide(event);
    }
}
