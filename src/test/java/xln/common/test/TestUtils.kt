package xln.common.test

import java.net.InetSocketAddress
import java.net.Socket

fun waitForPort(host: String, port: Int, timeoutMs: Long = 10000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), 200) }
            return
        } catch (e: Exception) {
            Thread.sleep(100)
        }
    }
    error("Port $host:$port not reachable after ${timeoutMs}ms")
}
