package com.IO.BIODemo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author XuHan
 * @date 2023/11/9 19:59
 */
public class InitNioAndNioReadServer {

    /**
     * accept 超时时间设置
     */
    private static final int SO_TIMEOUT = 2000;
    
    /**
     * 1. NIO 改写，accept 非阻塞
     * 2. 实现 read() 同样非阻塞
     *
     * @param port
     * @return void
     * @description
     * @author xander
     * @date 2023/7/12 16:38
     */
    public void initNioAndNioReadServer(int port) {
        ServerSocket serverSocket = null;//服务端Socket
        Socket socket = null;//客户端socket
        BufferedReader reader = null;
        ExecutorService threadPool = Executors.newCachedThreadPool();
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
                // 3. 拿到Socket 之后，应该使用线程池新开线程方式处理客户端连接，提高CPU利用率。（为多个客户端连接做的异步处理）
                //第一个问题的解决策略是启动多线程以非阻塞read()方式轮询，这样做的另一点好处是，某个Socket读写压力大并不会影响CPU 切到其他线程的正常工作。
                Thread thread = new Thread(new ClientSocketThread(socket));
                threadPool.execute(thread);
//                System.out.println(stringNowTime() + ": id为" + socket.hashCode() + "的Clientsocket connected");
//                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//
//                while ((inputContent = reader.readLine()) != null) {
//                    System.out.println("收到 id为" + socket.hashCode() + "  " + inputContent);
//                    count++;
//                }
//                System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (Objects.nonNull(reader)) {
                    reader.close();
                }
                if (Objects.nonNull(socket)) {

                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * 改写 客户端 Socket 连接为单独线程处理
     */
    class ClientSocketThread implements Runnable {

        private static final int SO_TIMEOUT = 2000;

        private static final int SLEEP_TIME = 1000;

        public final Socket socket;

        public ClientSocketThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            String inputContent;
            int count = 0;
            try {
                //解决第二点问题，我们需要为每个新的Socket设置 timeout。保证一个socket不会一直阻塞。
                socket.setSoTimeout(SO_TIMEOUT);
            } catch (SocketException e1) {
                e1.printStackTrace();
            }
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (true) {//这边这个轮询是为了解决socket.soTimeout的异常。
                    // 当调用read的时候，如果超时，会抛出异常，让cpu去干别的事情，然后过段时间再来读
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
                    //执行到这里表示读取到了数据，我们可以在这里进行回复客户端的工作 这边会一直打印读取结束因为这个线程一直没有终止
                    System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
                    Thread.sleep(SLEEP_TIME);
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    if (Objects.nonNull(reader)) {
                        reader.close();
                    }
                    if (Objects.nonNull(socket)) {
                        socket.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        InitNioAndNioReadServer server = new InitNioAndNioReadServer();
        server.initNioAndNioReadServer(8888);
    }
}
