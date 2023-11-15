package com.netty._1_SimpleServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;

/**
 * @author XuHan
 * @date 2023/11/15 10:11
 * 服务端启动失败自动递增端口号重新绑定端口。
 * 查看哪个端口被占用：sudo lsof -i tcp:port   lsof -i tcp:20
 * 测试端口是否开放: nc -zv 30.159.113.178 20
 * 列出所有端口：http://jartto.wang/2016/09/28/check-the-system-port-of-mac/
 * 列出正在使用的端口：netstat -an | grep LISTEN
 * 想要rebind效果出来，必须指定特定的ip地址，不然会绕过端口号低于1024的这个权限校验，端口没人用我就能绑定。
 */
public class NettyServerReBind {

    private static final int PORT = 1000;
    public static void main(String[] args) {
        NioEventLoopGroup boosGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .group(boosGroup, workerGroup)
                // 指定自定义属性，客户端可以根据此属性进行一些判断处理
                // 可以看作给Channel维护一个Map属性，这里的channel是服务端
                // 允许指定一个新创建的通道的初始属性。如果该值为空，指定键的属性将被删除。
                .attr(AttributeKey.newInstance("hello"), "hello world")
                // 给每个连接指定自定义属性，Channel 进行属性指定等
                // 用给定的值在每个 子通道 上设置特定的AttributeKey。如果该值为空，则AttributeKey将被删除。
                // 区别是是否是 子channel，子Channel代表给客户端的连接设置
                .childAttr(AttributeKey.newInstance("childAttr"), "childAttr")
                // 客户端的 Channel 设置TCP 参数
                // so_backlog 临时存放已完成三次握手的请求队列的最大长度，如果频繁连接可以调大此参数
                .option(ChannelOption.SO_BACKLOG, 1024)
                // 给每个连接设置TCP参数
                // tcp的心跳检测，true为开启
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                // nagle 算法开关，实时性要求高就关闭
                .childOption(ChannelOption.TCP_NODELAY, true)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                    }
                });


        bind(serverBootstrap, PORT);
    }

    /**
     * 自动绑定递增端口
     * @param serverBootstrap
     * @param port
     */
    public static void bind(ServerBootstrap serverBootstrap, int port){
        serverBootstrap.bind("30.159.113.178",port).addListener(future -> {
            if(future.isSuccess()){
                System.out.println("端口绑定成功");
                System.out.println("绑定端口"+ port +"成功");
            }else{
                System.out.println("端口绑定"+ port +"失败");
                System.out.println(future.cause());
                bind(serverBootstrap, port+1);
            }
        });
    }

}
