package xln.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

@Slf4j
public class NetUtils {

    public static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        }catch (Exception ex) {
            log.error("", ex);
            return "";
        }
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        }catch (Exception ex) {
            log.error("", ex);
            return "";
        }
    }
}
