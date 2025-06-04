package io.github.jerryt92.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public class GatewayStarter {
    private static String bindAddress = "0.0.0.0";

    private static int port = 8888;

    private static final Logger log = LogManager.getLogger(GatewayStarter.class);

    private static final String CERT_PATH = "cert/localhost.crt";

    private static final String KEY_PATH = "cert/localhost_pkcs8.key";

    public static void main(String[] args) {
        boolean ssl = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[i + 1]);
            }
            if (args[i].equals("--ssl")) {
                ssl = true;
            }
        }
        ServerBootstrap bootstrap = new ServerBootstrap();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ChannelHandler channelHandler = null;
            if (ssl) {
                ClassLoader classLoader = GatewayStarter.class.getClassLoader();
                try (InputStream certChainFile = classLoader.getResourceAsStream(CERT_PATH);
                     InputStream keyFile = classLoader.getResourceAsStream(KEY_PATH)) {
                    if (certChainFile == null || keyFile == null) {
                        throw new RuntimeException("Certificate or key file not found in classpath");
                    }
                    SslContext sslCtx = SslContextBuilder.forServer(certChainFile, keyFile).trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                    channelHandler = new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addFirst(sslCtx.newHandler(ch.alloc()));
                            ch.pipeline().addLast(generateRequestHandler(workerGroup));
                        }
                    };
                }
            } else {
                channelHandler = new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(generateRequestHandler(workerGroup));
                    }
                };
            }

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(channelHandler)
                    // 设置并发连接数
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .bind(InetAddress.getByName(bindAddress), port)
                    .sync()
                    .addListener(future -> {
                        if (future.isSuccess()) {
                            log.info("Gateway started at port {}", port);
                        } else {
                            log.error("Gateway started at port", future.cause());
                        }
                    })
                    .channel()
                    .closeFuture()
                    .sync();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static RequestHandler generateRequestHandler(EventLoopGroup workerGroup) {
        return new RequestHandler(workerGroup);
    }
}