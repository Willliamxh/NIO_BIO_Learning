package com.IO.BIODemo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * @author XuHan
 * @date 2023/11/9 17:01
 *  案例1：只为服务端设置timeOut。
 *
 * （1）服务端read的非阻塞轮询效率非常低，基本上是“一核繁忙、多核围观”的情况。（一旦一核进来了，就立马停下来，得同步处理，有结果了才返回）。
 * （2）第一次改造设置的是设定的是ServerSocket级别的SocksSocketImpl的timeout。
 *  每个新的客户端进来都是新的Socket连接，每个Socket又有各自的 SocksSocketImpl，
 *  这里客户端连接所产生新的Socket的timeout是没有做设置的，换句话说，
 *  服务端针对每个Socket的read依然是完全阻塞。（针对每个进来的socket的read都是阻塞的）
 *
 */
public class InitNIOServer {

    /**
     * accept 超时时间设置
     */
    private static final int SO_TIMEOUT = 2000;

    /***
     * NIO 改写
     * @description NIO 改写
     * @param port
     */
    public void initNioServer(int port) {
        ServerSocket serverSocket = null;//服务端Socket
        Socket socket = null;//客户端socket
        BufferedReader reader = null;
        String inputContent;
        int count = 0;
        try {
            serverSocket = new ServerSocket(port);
            // 1. 需要设置超时时间，会等待设置的时间之后再进行返回
            serverSocket.setSoTimeout(SO_TIMEOUT);
            System.out.println(stringNowTime() + ": serverSocket started");
            while (true) {
                // 2. 如果超时没有获取，这里会抛出异常，这里的处理策略是不处理异常
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    //运行到这里表示本次accept是没有收到任何数据的，服务端的主线程在这里可以做一些其他事情
                    System.out.println("now time is: " + stringNowTime());
                    continue;
                }
                System.out.println(stringNowTime() + ": id为" + socket.hashCode() + "的Clientsocket connected");
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while ((inputContent = reader.readLine()) != null) {
                    System.out.println("收到id为" + socket.hashCode() + "  " + inputContent);
                    count++;
                }
                System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(Objects.nonNull(reader)){

                    reader.close();
                }
                if(Objects.nonNull(socket)){

                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        InitNIOServer server = new InitNIOServer();
        server.initNioServer(8888);
    }
}
