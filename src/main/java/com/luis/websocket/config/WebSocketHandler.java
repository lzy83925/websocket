package com.luis.websocket.config;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.Date;

public class WebSocketHandler extends SimpleChannelInboundHandler<Object>{
    private WebSocketServerHandshaker webSocketServerHandshaker;
    public static final String WEB_SOCKET_URL="http://localhost:9999/websocket";

    /**
     * 服务端处理客户端websocket请求核心方法
     * @param channelHandlerContext
     * @param o
     * @throws Exception
     */
    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        //处理客户局向服务端发起http请求的业务
        if(o instanceof FullHttpRequest){
            handleHttpRequest(channelHandlerContext, (FullHttpRequest) o);

        }if(o instanceof WebSocketFrame){
            //处理websocket的业务
            handleWebSocketFrame(channelHandlerContext,(WebSocketFrame)o);
        }

    }

    /**
     * 处理客户端向服务端发起http握手请求的业务
     * @param channelHandlerContext
     * @param request
     */
    private void handleHttpRequest(ChannelHandlerContext channelHandlerContext,FullHttpRequest request){
        if(!request.decoderResult().isSuccess()||!("websocket".equals(request.headers().get("Upgrade")))){
            sendHttpResponse(channelHandlerContext,request,new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        WebSocketServerHandshakerFactory webSocketServerHandshakerFactory=new WebSocketServerHandshakerFactory(WEB_SOCKET_URL,null,false);
        webSocketServerHandshaker=webSocketServerHandshakerFactory.newHandshaker(request);
        if(webSocketServerHandshaker==null){
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channelHandlerContext.channel());
        }else{
            webSocketServerHandshaker.handshake(channelHandlerContext.channel(),request);
        }
    }

    /**
     * 服务端向客户端响应请求
     * @param channelHandlerContext
     * @param request
     * @param response
     */
    private void sendHttpResponse(ChannelHandlerContext channelHandlerContext, FullHttpRequest request, FullHttpResponse response){
        if(response.status().code()!=200){
            ByteBuf buf= Unpooled.copiedBuffer(response.status().toString(), CharsetUtil.UTF_8);
            response.content().writeBytes(buf);
            buf.release();
        }
        //服务端向客户端发送数据
        ChannelFuture channelFuture=channelHandlerContext.channel().writeAndFlush(response);
        if(response.status().code()!=200){
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理客户端和服务端之间的websocket业务
     * @param channelHandlerContext
     * @param webSocketFrame
     */
    private void handleWebSocketFrame(ChannelHandlerContext channelHandlerContext,WebSocketFrame webSocketFrame){
        //是否是websocket指令
        if(webSocketFrame instanceof CloseWebSocketFrame){
            webSocketServerHandshaker.close(channelHandlerContext.channel(),(CloseWebSocketFrame)webSocketFrame);
        }
        //是否是ping消息
        if(webSocketFrame instanceof PingWebSocketFrame){
            channelHandlerContext.channel().write(new PongWebSocketFrame(webSocketFrame.content().retain()));
            return;
        }
        //是否是二进制消息，如果是的话，抛出异常
        if(!(webSocketFrame instanceof TextWebSocketFrame)){
            System.out.println("不支持二进制消息...");
            throw  new RuntimeException(this.getClass().getName()+":不支持消息");
        }
        //返回应答消息
        //获取客户端向服务端发送的消息
        String request=((TextWebSocketFrame)webSocketFrame).text();
        System.out.println("服务端收到客户端的消息："+request);
        TextWebSocketFrame twsf=new TextWebSocketFrame(new Date().toString()+channelHandlerContext.channel().id()+"==============>>>>>>>>>>>>"+request);
        //群发
        NettyConfig.channelGroup.writeAndFlush(twsf);
    }



    /**
     * 客户端与服务端创建连接的时候调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.channelGroup.add(ctx.channel());
        System.out.println("客户端与服务端连接开启...");
    }



    /**
     * 客户端与服务端断开的时候调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.channelGroup.remove(ctx.channel());
        System.out.println("客户端与服务端连接关闭...");
    }

    /**
     * 服务端接收客户端数据结束后调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * 工程出现异常后调用
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
       cause.printStackTrace();
       ctx.close();
    }


}
