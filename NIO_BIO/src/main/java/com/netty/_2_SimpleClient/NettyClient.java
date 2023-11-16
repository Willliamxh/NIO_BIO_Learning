package com.netty._2_SimpleClient;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;

import java.util.Date;

/**
 * @author XuHan
 * @date 2023/11/15 15:27
 */
public class NettyClient {
    public static void main(String[] args) throws InterruptedException {
        //1.客户端连接不需要监听端口，为了和服务端区分直接被叫做Bootstrap，代表客户端的启动引导器。
        Bootstrap bootstrap = new Bootstrap();
        //2.Netty中客户端也同样需要设置线程模型才能和服务端正确交互，客户端的NioEventLoopGroup同样可以看作是线程池，负责和服务端的数据读写处理。
        NioEventLoopGroup eventExecutors = new NioEventLoopGroup();
        // 引导器引导启动
        bootstrap
                //客户端 group 线程池的设置只需要一个即可，因为主要目的是和服务端建立连接（只需要一个线程即可）。服务端有老板和员工
                .group(eventExecutors)
                //作用是底层编程模型的设置。官方注释中推荐使用NIO / EPOLL / KQUEUE这几种，使用最多的是NIO
                .channel(NioSocketChannel.class)
                //上文介绍服务端的时候提到过 handler()代表服务端启动过程当中的逻辑，在这里自然就表示客户端启动过程的逻辑，客户端的handler()可以直接看作服务端引导器当中的childHandler()。
                //这里读者可能会好奇为什么客户端代码不用childHandler呢？答案是Netty为了防止使用者误解，Bootstrap中只有handler，所以我们可以直接等同于服务端的childHandler()。
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast(new StringEncoder());
                    }
                });

        // 建立通道
        Channel channel = bootstrap.connect("127.0.0.1", 8000).channel();

        /**
         我们还可以用监听器对于连接失败的情况做自定义处理逻辑，比如下面例子将会介绍利用监听器实现客户端连接服务端失败之后，定时自动重连服务端多次直到重连次数用完的例子。
         */
        while (true){
            channel.writeAndFlush(new Date() + " Hello world");
            Thread.sleep(2000);
        }

    }

}
