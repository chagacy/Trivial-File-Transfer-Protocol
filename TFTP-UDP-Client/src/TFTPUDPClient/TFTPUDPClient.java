/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPUDPClient;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 *
 * @author chaya
 */
public class TFTPUDPClient {

    InetAddress address;
    final int packetSize = 516;

    byte OP_RRQ = 1;
    byte OP_WRQ = 2;
    byte OP_Data = 3;
    byte OP_Ack = 4;
    byte OP_Err = 5;

    byte[] buf;
    DatagramSocket socket;
    DatagramPacket outPacket; // packet o be sent
    DatagramPacket inPacket; // packet received
    Random r = new Random();
    int sourcePort = r.nextInt(65535 - 1024) + 1024; // random port
    int destPort = 1025; // main server port
    byte[] write = {};

    int block = 0;
    int expectedBlock = 0;

    boolean error = false;

    // the client will take the IP Address of the server (in dotted decimal format as an argument)
    // given that for this tutorial both the client and the server will run on the same machine, you can use the loopback address 127.0.0.1
    /**
     * The arguments passed to this should be first the ip address, then 1 for a
     * read request or 2 for a write request , and finally a filename to write
     * to the server or a file to write the read request into.
     * ----------------------------- | IP | REQ (1/2) | FILENAME |
     * -----------------------------
     *
     * @param args - command line
     * @throws IOException
     * @throws SocketException
     * @throws Exception
     */
    public static void main(String[] args) throws IOException, SocketException, Exception {
        if (args.length <= 2) {
            System.out.println("You must give arguments in the form (1) IP (2) REQ (1 or 2) (3) FILENAME ");
            return;
        }
        String fileName = args[2];
        TFTPUDPClient client = new TFTPUDPClient();
        client.get(fileName, args);

    }

    /**
     * This will take care of the whole transaction and make sure the data is
     * written to a file if a read request is done. it sets up the initial
     * connection and socket to 69. But, once the server makes a new thread with
     * a new socket then it will change.
     *
     * @param fileName - file you want to write to the server or the file you
     * want to write the read request into
     * @param args - command line
     * @throws UnknownHostException
     * @throws SocketException
     * @throws Exception
     */
    public void get(String fileName, String[] args) throws UnknownHostException, SocketException, Exception {
        //System.out.println("file name " + fileName);
        address = InetAddress.getByName(args[0]);
        socket = new DatagramSocket(sourcePort, address);
        String file = args[2]; //e.g. abc.txt

        byte op = 0;
        if (args[1].equals("1")) {
            op = OP_RRQ;
            //System.out.println("RRQ");
        } else if (args[1].equals("2")) {
            op = OP_WRQ;
            try {
                write = readContent(file);
            } catch (NoSuchFileException e) {
                socket.close();
                System.exit(0);
            }
            //System.out.println("readin size " + write.length);
            //System.out.println("WRQ");
        } else {
            System.err.println("Not a valid command. Enter 1 for read and 2 for write.");
            socket.close();
            System.exit(0); // exit as can't proceed with no correct command 
        }

        ByteArrayOutputStream reqByteArray = new ByteArrayOutputStream();
        reqByteArray.write(createRequest(op, fileName)); // puts the bytes in that are needed for a intital request
        byte[] req = reqByteArray.toByteArray(); // and then is put into a byte array
        //System.out.println("REQ length " + req.length);
        outPacket = new DatagramPacket(req, req.length, address, destPort); // using the servers intital port, makes req packet 

        // send request for a file 
        socket.send(outPacket);
        //System.out.println("Request sent");
        socket.setSoTimeout(2000); // puts a ticker on the socket so when ever a packet is needing to be received it will only wait this long

        //receive a file from the network
        ByteArrayOutputStream returned = collect();
        //write the file on to computer
        if (op == OP_RRQ && error == false) {
            writeFile(returned, fileName);
        }
        //Close the socket
        //System.out.println("Socket closed");
        socket.close(); //TIME_WAIT
    }

    /**
     * This sets up the byte array that will be used in the initial request
     * packet that will be sent to the server.
     *
     * @param opCode - the type of request
     * @param name - the file name you want to use
     * @return a byte array to put into a packet
     * @throws UnsupportedEncodingException
     */
    public byte[] createRequest(byte opCode, String name) throws UnsupportedEncodingException {
        String mode = "octet";
        byte[] rrqBytes = new byte[(2 + name.length() + 1 + mode.length() + 1)];
        int pointer = 0;

        rrqBytes[pointer] = 0;
        pointer++;
        rrqBytes[pointer] = opCode;
        pointer++;

        byte[] nameByte = name.getBytes("US-ASCII"); // convert filename into bytes
        for (byte n : nameByte) {
            rrqBytes[pointer] = n;
            pointer++;
        }
        rrqBytes[pointer] = (byte) 0; // end of file name
        pointer++;

        byte[] modeByte = mode.getBytes("US-ASCII");
        for (byte m : modeByte) {
            rrqBytes[pointer] = m;
            pointer++;
        }
        rrqBytes[pointer] = (byte) 0; // end of packet    
        return rrqBytes;
    }

    /**
     * This will deal will the communication between the server and client. It
     * will receive the packets with errors, acks or data. This will send the
     * correct packets back to maintain the connection and once all the data has
     * been sent or received will return.
     *
     * @return Byte stream of data that will be written into a packet if it's a
     * read request
     * @throws IOException
     */
    public ByteArrayOutputStream collect() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        buf = new byte[packetSize];
        inPacket = new DatagramPacket(buf, packetSize, address, socket.getLocalPort());
        int sentSoFar = 0; // used if writing a file to server
        boolean lastPacket = false; // recceived or sent alst packet
        boolean allSent = false; //  sent all the data and waiting for last ack 

        while (lastPacket == false) {
            int noToWrite = 0; // check
            try {
                socket.receive(inPacket);
            } catch (SocketTimeoutException ste) {
                //System.out.println("Timeout after 2 second");
                // need to resend last packet sent 
                socket.send(outPacket);
                //System.out.println("Resent packet ");
                // Need to check when i should retransmit and when I should just let the other retransmit.
            }

            byte[] opCode = {buf[0], buf[1]};

            if (opCode[1] == OP_Err) {
                lastPacket = true; // end connection
                error = true;
                System.err.println("File  you tried to read doesn't exist");
            } else if (opCode[1] == OP_Ack) { // start sending data to write
                if (allSent == true) {
                    lastPacket = true;
                    //System.out.println("Received last and can now close");
                    break;
                } else {
                    //System.out.println("recived ack");
                    ByteArrayOutputStream sendToWrite = new ByteArrayOutputStream();
                    byte[] blockNumber = incrementBlock(block); // may have to seperate up 
                    block++;
                    //System.out.println("blockNo sent " + block);

                    //send 512 bytes of what there is to write
                    int amount = write.length;
                    //System.out.println("Amount " + amount);
                    int amountLeft = 0;
                    if ((amount - sentSoFar) / 512 >= 1) {
                        amountLeft = 512;
                    } else {
                        amountLeft = (amount - sentSoFar);
                        //System.out.println("amount left " + amountLeft);
                    }

                    int sentSoFarInner = sentSoFar;
                    for (int i = sentSoFar; i < (sentSoFar + amountLeft); i++) {
                        sendToWrite.write(write[i]);
                        sentSoFarInner++;
                    }
                    sentSoFar = sentSoFarInner;

                    if (sentSoFar == amount) {
                        allSent = true;
                        //System.out.println("Sent last of the data");
                    }

                    byte[] dataToSend = sendToWrite.toByteArray();
                    byte[] dataStart = {0, OP_Data, blockNumber[0], blockNumber[1]};
                    byte[] data = new byte[dataToSend.length + dataStart.length];

                    System.arraycopy(dataStart, 0, data, 0, dataStart.length); // check this works
                    System.arraycopy(dataToSend, 0, data, dataStart.length, dataToSend.length);

                    outPacket = new DatagramPacket(data, data.length, address, inPacket.getPort());

                    socket.send(outPacket);
                    //System.out.println("send data to write");

                    if (sentSoFar == amount && amount % 512 == 0) { // this will check if the last packet is 512 if it is then a empty packet needs to be sent
                            blockNumber = incrementBlock(block);
                            block++;
                            
                            byte[] emptyP = {0, OP_Data, blockNumber[0], blockNumber[1]};
                            outPacket = new DatagramPacket(emptyP, emptyP.length, address, inPacket.getPort());
                            
                            socket.send(outPacket);
                            allSent = true;
                            //System.out.println("Sent EMPTY packet of data");
                    }
                }
            } else if (opCode[1] == OP_Data) { // send dback an Ack and do something with the data
                //System.out.println("Data packet client");
                byte[] blockNumber = {buf[2], buf[3]};

                byte[] expectedBlockNo = incrementBlock(expectedBlock);
                if (blockNumber[0] == expectedBlockNo[0] && blockNumber[1] == expectedBlockNo[1]) {
                    expectedBlock++;

                    if (inPacket.getLength() < 516) { // checks if last packet by seeing if its a data packet and its not full if it ack is sent then socket will be closed
                        lastPacket = true;
                        //System.out.println("LASTPACKET now TRUE");
                    }

                    //System.out.println("Passed block number");
                    //DataOutputStream dos = new DataOutputStream(out); // puts it into out -- this turns it back into chars
                    out.write(inPacket.getData(), 4, inPacket.getLength() - 4); // gets the data out of the byte and puts into the buffer
                    //////////////////////// SENDING THE ACK
                    byte[] ack = {0, OP_Ack, blockNumber[0], blockNumber[1]};

                    outPacket = new DatagramPacket(ack, ack.length, address, inPacket.getPort());

                    socket.send(outPacket);
                    //System.out.println("Ack sent");
                } else {
                    //System.out.println("fails block number");
                }
            }

        }
        //System.out.println("Finished");
        return out;
    }

    /**
     * The byte array given to it is written to the file
     *
     * @param bOut - byte array containing the data from the server
     * @param fileName - file to write it to
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void writeFile(ByteArrayOutputStream bOut, String fileName) throws FileNotFoundException, IOException { // if read then write to a file
        OutputStream outputStream = new FileOutputStream(fileName);
        outputStream.write(bOut.toByteArray());
    }

    /**
     * Will read the content of the file into a byte array
     *
     * @param fileName - file you want to read the data in from
     * @return the byte array of the files content
     * @throws IOException
     */
    private byte[] readContent(String fileName) throws IOException {
        Path p = Paths.get(fileName);

        byte[] fileContent = Files.readAllBytes(p);//new File(System.getProperty("user.dir")+ "\\src\\udpserver\\" + fileName).toPath());//input stream is  UDPResponder.class.getResourceAsStream(fileName)
        return fileContent;
    }

    /**
     * Increments block number, turns it into a byte and takes the first 8 bits
     * and puts into a byte array and then takes the send lot of 8 bits and puts
     * into the second cell
     *
     * @param blockNo - the block number that needs to be incremented and
     * converted
     * @return byte array of the block number separated up
     */
    public byte[] incrementBlock(int blockNo) {
        blockNo++;
        byte[] b = new byte[2];
        b[0] = (byte) (blockNo & 0xFF); // take the first 8 bits
        b[1] = (byte) ((blockNo >> 8) & 0xFF); // rotate by 8 and then take those 8 bits
        return b;
    }
}
