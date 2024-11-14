package io.github.jerryt92.proxy.http;

import io.github.jerryt92.proxy.http.route.DefaultHttpRouteRule;
import io.github.jerryt92.proxy.http.route.IHttpRouteRule;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
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
public class HttpRequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LogManager.getLogger(HttpRequestHandler.class);
    private final IHttpRouteRule httpRouteRule;

    public HttpRequestHandler() {
        this.httpRouteRule = new DefaultHttpRouteRule();
    }

    public HttpRequestHandler(IHttpRouteRule httpRouteRule) {
        this.httpRouteRule = httpRouteRule;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        try {
            log.info("Src address: {}", ctx.channel().remoteAddress());
            log.info("Dst address: {}", ctx.channel().localAddress());
            Map.Entry<String, Integer> route = parseHostAndPort(ctx, msg);
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
                ProxyChannelCache.getChannelClientCache().remove(ctx.channel());
            }
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.AUTO_READ, true);
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
                                    new HttpResponseHandler(ctx.channel())
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

    Map.Entry<String, Integer> parseHostAndPort(ChannelHandlerContext ctx, Object msg) {
        try {
            ByteBuf msgCopy = ((ByteBuf) msg).copy();
            EmbeddedChannel httpRequestDecoder = new EmbeddedChannel(new HttpRequestDecoder());
            httpRequestDecoder.writeInbound(msgCopy);
            HttpRequest request = httpRequestDecoder.readInbound();
            if (request == null || request.headers() == null) {
                // Get route from cache
                return ProxyChannelCache.getChannelRouteCache().get(ctx.channel());
            }
            Map.Entry<String, Integer> route = httpRouteRule.getRoute(request);
            ProxyChannelCache.getChannelRouteCache().put(ctx.channel(), route);
            return route;
        } catch (Exception e) {
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
            cause.printStackTrace();
            closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ProxyChannelCache.getChannelClientCache().remove(ctx.channel());
        ProxyChannelCache.getChannelRouteCache().remove(ctx.channel());
        super.channelInactive(ctx);
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
