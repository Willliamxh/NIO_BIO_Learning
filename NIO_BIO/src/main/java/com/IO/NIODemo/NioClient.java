package com.IO.NIODemo;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;

/**
 * @author XuHan
 * @date 2023/11/10 15:42
 */
public class NioClient {

        public static void main(String[] args) {
            new Thread(() -> {
                try {
                    Socket socket = new Socket("127.0.0.1", 8000);
                    while (true) {
                        try {
                            socket.getOutputStream().write((new Date() + ": hello world").getBytes());
                            socket.getOutputStream().flush();
                            Thread.sleep(2000);
                        } catch (Exception e) {
                        }
                    }
                } catch (IOException e) {
                }
            }).start();
        }

}
