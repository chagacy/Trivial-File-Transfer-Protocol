/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPTCPServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 *
 * @author chaya
 */
public class TFTPTCPServer {

    int defaultPort = 69;
    ServerSocket socket;

    public static void main(String[] args) throws IOException {
        //System.out.println("Time Server Started");
        TFTPTCPServer server = new TFTPTCPServer();
    }

    /**
     * Setup the socket up with default port. This is where all requests will
     * come to. When it receives a packet it will create a new thread to deal
     * with the transaction which will take place.
     *
     * @throws SocketException
     * @throws IOException
     */
    public TFTPTCPServer() throws SocketException, IOException {
        this.socket = new ServerSocket(defaultPort);
        while (true) {
            Socket clientSocket = socket.accept();
            //System.out.println("Server waiting for packet");
            new Thread(new TFTPTCPResponder(clientSocket)).start();
            //System.out.println("New Thread Server Started");

        }
    }
}
