package io.github.jerryt92.proxy;

import io.github.jerryt92.proxy.protocol.ProtocolType;
import io.github.jerryt92.proxy.protocol.route.DefaultHttpRouteRule;
import io.github.jerryt92.proxy.protocol.route.IHttpRouteRule;
import io.github.jerryt92.proxy.protocol.tcp.ProtocolDetection;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public class RequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LogManager.getLogger(RequestHandler.class);
    private final IHttpRouteRule httpRouteRule;
    private final EventLoopGroup workerGroup;

    public RequestHandler(EventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
        this.httpRouteRule = new DefaultHttpRouteRule();
    }

    public RequestHandler(EventLoopGroup workerGroup, IHttpRouteRule httpRouteRule) {
        this.workerGroup = workerGroup;
        this.httpRouteRule = httpRouteRule;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        try {
            log.info("Src address: {}", ctx.channel().remoteAddress());
            log.info("Dst address: {}", ctx.channel().localAddress());
            Map.Entry<String, Integer> route = getRoute(ctx, msg);
            if (route == null) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            String remoteHost = route.getKey();
            int remotePort = route.getValue();
            if (ProxyChannelCache.getChannelClientCache().containsKey(ctx.channel())) {
                Channel channel = ProxyChannelCache.getChannelClientCache().get(ctx.channel());
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(msg);
                    return;
                }
                if (channel != null && !channel.isActive()) {
                    // 显式关闭连接
                    channel.close();
                }
                ProxyChannelCache.getChannelClientCache().remove(ctx.channel());
            }
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.handler(
                    new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws SSLException {
                            if (checkTargetSupportHttps(remoteHost, remotePort)) {
                                ch.pipeline().addLast(
                                        SslContextBuilder.forClient()
                                                .trustManager(InsecureTrustManagerFactory.INSTANCE).build().newHandler(ch.alloc(), remoteHost, remotePort)
                                );
                            }
                            ch.pipeline().addLast(
                                    new ResponseHandler(ctx.channel())
                            );
                        }
                    }
            );
            ChannelFuture f = b.connect(remoteHost, remotePort).sync();
            Channel channel = f.channel();
            ProxyChannelCache.getChannelClientCache().put(ctx.channel(), channel);
            channel.writeAndFlush(msg);
        } catch (Exception e) {
            exceptionCaught(ctx, e);
        }
    }

    Map.Entry<String, Integer> getRoute(ChannelHandlerContext ctx, Object msg) {
        try {
            ByteBuf msgByteBuf = (ByteBuf) msg;
            // Get route from cache
            Map.Entry<String, Integer> route = ProxyChannelCache.getChannelRouteCache().get(ctx.channel());
            if (route != null) {
                return route;
            }
            ByteBuf msgCopy = msgByteBuf.copy();
            // 识别第一个数据包协议，获取对应的路由策略
            // Detect first packet protocol to get corresponding routing strategy
            ProtocolType protocol = ProtocolDetection.detectProtocol(msgByteBuf);
            log.info("Protocol: {}", protocol);
            switch (protocol) {
                case HTTP:
                    EmbeddedChannel httpChannel = new EmbeddedChannel(new HttpRequestDecoder());
                    httpChannel.writeInbound(msgCopy);
                    HttpRequest request = httpChannel.readInbound();
                    if (request == null || request.headers() == null) {
                        return null;
                    }
                    route = httpRouteRule.getRoute(request);
                    ProxyChannelCache.getChannelRouteCache().put(ctx.channel(), route);
                    break;
                default:
                    log.error("Unsupported protocol: {}", protocol);
                    break;
            }
            return route;
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    private Boolean checkTargetSupportHttps(String host, int port) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.startHandshake();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            // 处理超时异常，返回404状态码
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            log.error("", cause);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProxyChannelCache.getChannelClientCache().remove(ctx.channel());
        ProxyChannelCache.getChannelRouteCache().remove(ctx.channel());
        super.channelInactive(ctx);
    }
}
