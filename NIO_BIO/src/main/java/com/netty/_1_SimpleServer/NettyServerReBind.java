package com.netty._1_SimpleServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

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
                .option(ChannelOption.SO_REUSEADDR, true)//
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
