package xln.common.dist;

import lombok.val;
import xln.common.utils.NetUtils;

import java.util.Base64;
import java.util.Random;

public class KeyUtils {

    private static Random random = new Random();

    public static String getNodeKey(String appName, String phase) {
        var bytes = new byte[4];
        random.nextBytes(bytes);
        val host = NetUtils.getHostName() + "-" + NetUtils.getHostAddress() + "-" + Base64.getEncoder().encodeToString(bytes);
        return getNodeDirectory(appName, phase) + host;
    }
    public static String getControllerNode(String appName, String phase) {

        return "apps/" + appName + "-" + phase + "/controller";
    }

    public static String getNodeDirectory(String appName, String phase) {
        return "apps/" + appName + "-" + phase + "/nodes/";

    }

    public static String getEndKey(String startKey)  {

        StringBuilder strBuilder = new StringBuilder();
        return strBuilder.append(startKey).replace(startKey.length() - 2, startKey.length() -1, "~").toString();
    }

}
