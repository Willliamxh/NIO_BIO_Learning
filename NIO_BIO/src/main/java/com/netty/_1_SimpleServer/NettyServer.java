package com.netty._1_SimpleServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;

/**
 * @author XuHan
 * @date 2023/11/14 20:33
 */
public class NettyServer {
    public static void main(String[] args) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();

        //boos和JDK的NIO编程一样负责进行新连接的“轮询”，他会定期检查客户端是否已经准备好可以接入
        NioEventLoopGroup boos = new NioEventLoopGroup();

        //worker则负责处理boss获取到的连接，当检查连接有数据可以读写的时候就进行数据处理。
        NioEventLoopGroup worker = new NioEventLoopGroup();
        /*
        其实这两个Group对象简单的看成是线程池即可，和JDBC的线程池没什么区别。通过阅读源码可以知道，
        bossGroup只用了一个线程来处理远程客户端的连接，workerGroup 拥有的线程数默认为2倍的cpu核心数。
        那么这两个线程池是如何配合的？boss和worker的工作模式和我们平时上班，老板接活员工干活的模式是类似的。
        由bossGroup负责接待，再转交给workerGroup来处理具体的业务。
         */
        //服务端引导类是ServerBootstrap，引导器指的是引导开发者更方便快速的启动Netty服务端/客户端，用到了建造者模式
        serverBootstrap
                //group方法绑定boos和work使其各司其职，这个操作可以看作是绑定线程池。
                .group(boos, worker)
                //设置底层编程模型或者说底层通信模式，一旦设置中途不允许更改。
                .channel(NioServerSocketChannel.class)
                //childHandler方法主要作用是初始化和定义处理链来处理请求处理的细节。
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    protected void initChannel(NioSocketChannel ch) {
                        //Handler负责处理一个I/O事件或拦截一个I/O操作，处理完成将其转发给其ChannelPipeline中的下一个处理Handler，以此形成经典的处理链条。
                        ch.pipeline().addLast(new StringDecoder());
                        //比如案例里面StringDecoder解码处理数据之后将会交给SimpleChannelInboundHandler的channelRead0方法，该方法中将解码读取到的数据打印到控制台。
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                System.out.println(msg);
                            }
                        });
                    }
                })
                .bind(8000);
    }

}
