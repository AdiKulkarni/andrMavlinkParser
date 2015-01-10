
package com.andrMavlinkParser;

import com.MAVLink.Parser;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.Messages.MAVLinkPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class ParseCommands {

    private static final int UDP_SERVER_PORT = 4003;
    private static final int MAX_UDP_DATAGRAM_LEN = 1024;
    private DatagramSocket ds = null;
    private InetAddress IPAddress;
    private int port;
    private DatagramPacket sendPacket;

    private byte[] data = null;;
    private Byte b;

    public void runGpsParser() {

        Parser commandParser = new Parser();
        MAVLinkPacket mavPkt = null;

        byte[] lMsg = new byte[MAX_UDP_DATAGRAM_LEN];
        DatagramPacket dp = new DatagramPacket(lMsg, lMsg.length);
        try {
            ds = new DatagramSocket(UDP_SERVER_PORT);

            // disable timeout for testing
            // ds.setSoTimeout(100000);

            ds.receive(dp);
            IPAddress = dp.getAddress();
            port = dp.getPort();

            // Receive data byte by byte and feed to this function
            while (true) {
                data = SerialConsoleActivity.getDataByte();
                if (data != null) {
                    for (int i = 0; i < data.length; i++) {
                        b = data[i];
                        mavPkt = commandParser.mavlink_parse_char(b & 0xff);
                        if (mavPkt != null) {
                            MAVLinkMessage mavMessage = mavPkt.unpack();
                            sendPacket = new DatagramPacket(
                                    mavMessage.toString().getBytes(),
                                    mavMessage.toString().getBytes().length,
                                    IPAddress, port);
                            // Send datagram to server
                            ds.send(sendPacket);
                        }
                    }
                }
                else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
            // Forward the packet to mission planner
        } catch (SocketException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }
}
