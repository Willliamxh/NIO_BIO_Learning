package com.xuhan.BIODemo;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author XuHan
 * @date 2023/11/3 14:44
 */
public class BIOClient2 {

    public void initBIOClient(String host, int port) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        Socket socket = null;
        String inputContent;
        int count = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(System.in));
            socket = new Socket(host, port);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("clientSocket started: " + stringNowTime());
            while (((inputContent = reader.readLine()) != null) && count < 2) {
                inputContent = new StringBuilder().append(stringNowTime()).append(": 第").append(count).append("条消息: ").append(inputContent).append("\n").toString();
                //将消息发送给服务端
                writer.write(inputContent);
                writer.flush();
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                reader.close();
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String stringNowTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new Date());
    }

    public static void main(String[] args) {
        BIOClient2 client = new BIOClient2();
        client.initBIOClient("127.0.0.1", 8888);
    }

}
