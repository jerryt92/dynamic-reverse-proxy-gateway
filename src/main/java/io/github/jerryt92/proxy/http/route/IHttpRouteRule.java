package io.github.jerryt92.proxy.http.route;

import io.netty.handler.codec.http.HttpRequest;

import java.util.Map;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public interface IHttpRouteRule {
    /**
     * Get route from HttpRequest
     *
     * @param request HttpRequest
     * @return Route K: host, V: port
     */
    Map.Entry<String, Integer> getRoute(HttpRequest request);
}
