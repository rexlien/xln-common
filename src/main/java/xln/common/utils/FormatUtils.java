package xln.common.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class FormatUtils {

    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static String formatDate(long epochMillis) {
        return formatDate(epochMillis, DEFAULT_FORMAT);
    }

    public static String formatDate(long epochMillis, String formatPattern) {
        var date = new Date(epochMillis);
        var format = new SimpleDateFormat(formatPattern);
        return format.format(date);

    }
}
