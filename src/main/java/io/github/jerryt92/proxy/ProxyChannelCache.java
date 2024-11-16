package io.github.jerryt92.proxy;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public class ProxyChannelCache {
    private static final ProxyChannelCache INSTANCE = new ProxyChannelCache();

    private static ProxyChannelCache getInstance() {
        return INSTANCE;
    }

    private ProxyChannelCache() {
    }

    /**
     * 通道的代理客户端缓存
     * Channel's proxy client cache
     */
    private final ConcurrentHashMap<Channel, Channel> channelClientCache = new ConcurrentHashMap<>();
    /**
     * 通道的Http路由缓存
     * Channel's Http route cache
     */
    private final ConcurrentHashMap<Channel, Map.Entry<String, Integer>> channelRouteCache = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<Channel, Channel> getChannelClientCache() {
        ProxyChannelCache instance = getInstance();
        return instance.channelClientCache;
    }

    public static ConcurrentHashMap<Channel, Map.Entry<String, Integer>> getChannelRouteCache() {
        ProxyChannelCache instance = getInstance();
        return instance.channelRouteCache;
    }
}
