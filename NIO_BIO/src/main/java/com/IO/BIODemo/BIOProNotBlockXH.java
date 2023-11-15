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

/**
 * @author XuHan
 * @date 2023/11/6 20:28
 * 尝试不使用线程池，只设置一个timeOut
 * 这个和InitNIOServer是一个意思
 */
public class BIOProNotBlockXH {

    public void initBIOServer(int port) {
        ServerSocket serverSocket = null;//服务端Socket
        Socket socket = null;//客户端socket
        // ExecutorService threadPool = Executors.newCachedThreadPool();
        // BIOProNotBlockXH.ClientSocketThread thread = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000);
            System.out.println(stringNowTime() + ": serverSocket started");
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    //运行到这里表示本次accept是没有收到任何数据的，服务端的主线程在这里可以做一些其他事情
                    System.out.println("now time is: " + stringNowTime());
                    continue;
                }

                try {
                    //解决第二点问题，我们需要为每个新的Socket设置 timeout。保证一个socket不会一直阻塞。
                    socket.setSoTimeout(1000);
                } catch (SocketException e1) {
                    e1.printStackTrace();
                }

                System.out.println(stringNowTime() + ": id为" + socket.hashCode() + "的Clientsocket connected");
                // 测试不用线程池
                BufferedReader reader = null;
                String inputContent;
                int count = 0;
                try {
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
                    System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        reader.close();
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // thread = new BIOProNotBlockXH.ClientSocketThread(socket);
                // threadPool.execute(thread);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return format.format(new Date());
    }

    class ClientSocketThread extends Thread {
        public Socket socket;

        public ClientSocketThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            String inputContent;
            int count = 0;
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while ((inputContent = reader.readLine()) != null) {
                    System.out.println("收到id为" + socket.hashCode() + "  " + inputContent);
                    count++;
                }
                System.out.println("id为" + socket.hashCode() + "的Clientsocket " + stringNowTime() + "读取结束");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    reader.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        BIOProNotBlockXH server = new BIOProNotBlockXH();
        server.initBIOServer(8888);
    }
}
