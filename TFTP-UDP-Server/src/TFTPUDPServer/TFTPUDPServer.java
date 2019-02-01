/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPUDPServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 *
 * @author chaya
 */
public class TFTPUDPServer {

    int defaultPort = 1025;
    DatagramSocket socket;

    public static void main(String[] args) throws IOException {
        //System.out.println("Time Server Started");
        TFTPUDPServer server = new TFTPUDPServer();
    }

    /**
     * Setup the socket up with default port. This is where all requests will
     * come to. When it receives a packet it will create a new thread to deal
     * with the transaction which will take place.
     *
     * @throws SocketException
     * @throws IOException
     */
    public TFTPUDPServer() throws SocketException, IOException {
        this.socket = new DatagramSocket(defaultPort);
        while (true) {
            byte[] inBuf = new byte[516];
            DatagramPacket packet = new DatagramPacket(inBuf, 516);
            //System.out.println("Server waiting for packet");
            socket.receive(packet);
            new Thread(new TFTPUDPResponder(packet)).start();
            //System.out.println("New Thread Server Started");
        }
    }
}
