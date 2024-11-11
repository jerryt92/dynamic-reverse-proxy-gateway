package io.github.jerryt92.proxy.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.AbstractMap;
import java.util.Map;

public class HttpRequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LogManager.getLogger(HttpRequestHandler.class);

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            log.info("Src address: {}", ctx.channel().remoteAddress());
            log.info("Dst address: {}", ctx.channel().localAddress());
            Map.Entry<String, Integer> route = parseHostAndPort(ctx, (HttpRequest) msg);
            if (route == null) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            String remoteHost = route.getKey();
            int remotePort = route.getValue();
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
                                    new HttpRequestEncoder(),
                                    new HttpResponseDecoder(),
                                    new HttpResponseHandler(ctx.channel())
                            );
                        }
                    }
            );
            ChannelFuture f = b.connect(remoteHost, remotePort).sync();
            f.channel().writeAndFlush(msg);
        } catch (Exception e) {
            exceptionCaught(ctx, e);
        }
    }

    Map.Entry<String, Integer> parseHostAndPort(ChannelHandlerContext ctx, HttpRequest request) {
        String host = request.headers().get("Host");
        String targetHost = host.split("\\.proxy")[0];
        if (StringUtils.isBlank(targetHost)) {
            return null;
        }
        // Split the targetHost by the last dot to separate the host and port
        int lastDotIndex = targetHost.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return null;
        }
        String hostPart = targetHost.substring(0, lastDotIndex);
        String portPart = targetHost.substring(lastDotIndex + 1);
        try {
            int port = Integer.parseInt(portPart);
            return new AbstractMap.SimpleEntry<>(hostPart, port);
        } catch (NumberFormatException e) {
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

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
