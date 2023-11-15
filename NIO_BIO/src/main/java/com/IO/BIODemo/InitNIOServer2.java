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
 * 案例2:
 * 给服务端和客户端都设置了timeOut
 *
 * 只设置socketTimeOut，并捕获异常，但是没有多线程去处理多个客户端。
 * 这会导致我们一个客户端接入进来后，由于用while true来轮询这个客户端是否来数据了
 *
 * （1）服务端read的非阻塞轮询效率非常低，基本上是“一核繁忙、多核围观”的情况。（一旦一核进来了，就立马停下来，得同步处理，有结果了才返回）。
 * （2）第一次改造设置的是设定的是ServerSocket级别的SocksSocketImpl的timeout。
 *  每个新的客户端进来都是新的Socket连接，每个Socket又有各自的 SocksSocketImpl，
 *  这里客户端连接所产生新的Socket的timeout是没有做设置的，换句话说，
 *  服务端针对每个Socket的read依然是完全阻塞。（针对每个进来的socket的read都是阻塞的）
 *
 *  1.多线程是为了能够接入多个客户端，保证在一个客户端有问题的时候，其它线程能够接入进来。
 *  2.socket的timeOut是为了解决单客户端的阻塞问题，当一个服务器读操作超过一定时间之后，直接断掉，然后重新去读socket的信息
 */
public class InitNIOServer2 {

    /**
     * accept 超时时间设置
     */
    private static final int SO_TIMEOUT = 2000;

    private static final int SLEEP_TIME = 1000;

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
                    socket.setSoTimeout(SO_TIMEOUT);
                } catch (SocketTimeoutException e) {
                    //运行到这里表示本次accept是没有收到任何数据的，服务端的主线程在这里可以做一些其他事情
                    System.out.println("now time is: " + stringNowTime());
                    continue;
                }
                System.out.println(stringNowTime() + ": id为" + socket.hashCode() + "的Clientsocket connected");
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (true) {//这边这个轮询是为了解决socket.soTimeout的异常。
                    try {
                        while ((inputContent = reader.readLine()) != null) {
                            System.out.println("收到id为" + socket.hashCode() + "  " + inputContent);
                            count++;
                        }
                    } catch (Exception e) {
                        //执行到这里表示read方法没有获取到任何数据，线程可以执行一些其他的操作
                        System.out.println("Not read data: " + stringNowTime());
                        continue;
                    }
                    //执行到这里表示读取到了数据，我们可以在这里进行回复客户端的工作
                    System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
                    Thread.sleep(SLEEP_TIME);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
        InitNIOServer2 server = new InitNIOServer2();
        server.initNioServer(8888);
    }
}
