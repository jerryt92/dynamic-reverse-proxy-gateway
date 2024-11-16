package io.github.jerryt92.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @Date: 2024/11/11
 * @Author: jerryt92
 */
public class ResponseHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;

    public ResponseHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!inboundChannel.isActive()) {
            RequestHandler.closeOnFlush(ctx.channel());
        } else {
            ctx.read();
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        inboundChannel.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        RequestHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        RequestHandler.closeOnFlush(ctx.channel());
    }
}
