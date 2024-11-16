package io.github.jerryt92.proxy.protocol.route;

import io.netty.handler.codec.http.HttpRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Map;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public class DefaultHttpRouteRule implements IHttpRouteRule {
    private static final Logger log = LogManager.getLogger(DefaultHttpRouteRule.class);

    @Override
    public Map.Entry<String, Integer> getRoute(HttpRequest request) {
        try {
            String host = request.headers().get("Host");
            String targetHost = (host == null) ? null : host.split("\\.proxy")[0];
            if (StringUtils.isEmpty(host) || StringUtils.isEmpty(targetHost)) {
                return null;
            }
            int lastDotIndex = targetHost.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return null;
            }
            String hostPart = targetHost.substring(0, lastDotIndex);
            String portPart = targetHost.substring(lastDotIndex + 1);
            int port = Integer.parseInt(portPart);
            return new AbstractMap.SimpleEntry<>(hostPart, port);
        } catch (Exception e) {
            log.error("Failed to parse host and port", e);
            return null;
        }
    }
}
