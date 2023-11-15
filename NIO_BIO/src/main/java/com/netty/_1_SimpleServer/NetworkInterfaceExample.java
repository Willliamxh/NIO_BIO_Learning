package com.netty._1_SimpleServer;

/**
 * @author XuHan
 * @date 2023/11/15 13:34
 * https://stackoverflow.com/questions/29958143/what-are-en0-en1-p2p-and-so-on-that-are-displayed-after-executing-ifconfig
 */
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkInterfaceExample {
    public static void main(String[] args) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                System.out.println("Interface: " + networkInterface.getName());
                System.out.println("  Display Name: " + networkInterface.getDisplayName());
                System.out.println("  MAC Address: " + formatMacAddress(networkInterface.getHardwareAddress()));
                System.out.println("  IP Addresses:");
                Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    System.out.println("    " + address.getHostAddress());
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatMacAddress(byte[] macAddress) {
        if (macAddress == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < macAddress.length; i++) {
            sb.append(String.format("%02X%s", macAddress[i], (i < macAddress.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }
}

