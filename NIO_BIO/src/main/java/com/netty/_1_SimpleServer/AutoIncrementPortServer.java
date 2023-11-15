package com.netty._1_SimpleServer;

/**
 * @author XuHan
 * @date 2023/11/15 11:56
 */
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class AutoIncrementPortServer {
    private static final int INITIAL_PORT = 1000; // 初始端口号

    public static void main(String[] args) throws InterruptedException {
        int port = INITIAL_PORT;
        while (true) {
            try {
                startServer(port);
                break; // 如果成功绑定端口，跳出循环
            } catch (Exception e) {
                System.err.println("Failed to start server on port: " + port);
                port++; // 端口号递增
            }
        }
    }

    private static void startServer(int port) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            // 添加自定义的处理器和逻辑
                            pipeline.addLast(new YourCustomServerHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind("0.0.0.0",port).sync();
            System.out.println("Server started on port: " + port);

            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static class YourCustomServerHandler extends ChannelInboundHandlerAdapter {
        // 自定义处理器和逻辑
    }
}
