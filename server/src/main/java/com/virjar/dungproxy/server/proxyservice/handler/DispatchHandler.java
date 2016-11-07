package com.virjar.dungproxy.server.proxyservice.handler;

import com.google.common.base.Charsets;
import com.virjar.dungproxy.server.proxyservice.common.ProxyResponse;
import com.virjar.dungproxy.server.proxyservice.common.util.NetworkUtil;
import com.virjar.dungproxy.server.proxyservice.handler.checker.ConnectMethodValidator;
import com.virjar.dungproxy.server.proxyservice.handler.checker.RequestValidator;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.AttributeKey;
import org.apache.http.impl.client.ProxyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.util.AttributeKey.valueOf;

/**
 * Description: DispatchHandler
 *
 * @author lingtong.fu
 * @version 2016-10-18 16:16
 */

@ChannelHandler.Sharable
public class DispatchHandler extends ClientProcessHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchHandler.class);

    private String serverHost;
    // 暂用 apache tunnel proxyClient.
    public static final ProxyClient proxyClient = new ProxyClient();

    public DispatchHandler(String serverHost) {
        this.serverHost = serverHost;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            // 储存service
            Channel c = ctx.channel();
            AttributeKey<ProxyClient> PROXY_CLIENT = valueOf("proxyClient");
            c.attr(PROXY_CLIENT).set(proxyClient);
            ChannelPipeline pipeline = ctx.pipeline();
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                if (request.decoderResult().isFailure()) {
                    LOGGER.info("[{}] Bad Request [{}] Cause [{}]", ctx.channel(), request.uri(), request.decoderResult().cause().getMessage());
                    NetworkUtil.resetHandler(pipeline, BadRequestHandler.instance);
                } else {
                    //
                    NetworkUtil.removeHandler(pipeline, HttpResponseEncoder.class);
                    NetworkUtil.addHandlerIfAbsent(pipeline, ConnectMethodValidator.instance);
                    NetworkUtil.addHandlerIfAbsent(pipeline, RequestValidator.instance);
                }
            } else {
                ByteBuf bb = (ByteBuf) msg;
                LOGGER.info("[{}] Bad Request Content:\n {}", ctx.channel(), bb.toString(Charsets.UTF_8));
                NetworkUtil.resetHandler(pipeline, BadRequestHandler.instance);
            }
            super.channelRead(ctx, msg);
        } catch (Exception e) {
            String log;
            if (msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;
                log = String.format("Channel 发生异常 [%s] [%s] MTD [%s] URL [%s] Headers [%s]", ctx.channel(), request.protocolVersion().toString(), request.method(), request.uri(), request.headers().entries());
            } else {
                log = String.format("Channel 发生异常 [%s]", ctx.channel());
            }
            NetworkUtil.releaseMsgCompletely(msg);
            LOGGER.error(log, e);
            NetworkUtil.writeAndFlushAndClose(ctx.channel(), ProxyResponse.proxyError(0, e.getClass().getName(), Long.toHexString(ctx.channel().hashCode())));
        }
    }
}
