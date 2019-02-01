/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPUDPServer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 *
 * @author chaya
 */
public class TFTPUDPResponder implements Runnable {

    InetAddress address; // ip address
    int port; // port number
    protected DatagramSocket socket = null;
    DatagramPacket inPacket; // packet received
    DatagramPacket outPacket; // packet to be sent out
    Random r = new Random();
    final int defaultPort = r.nextInt(65535 - 1024) + 1024; // random port between 1024 and 65535

    byte OP_RRQ = 1;
    byte OP_WRQ = 2;
    byte OP_Data = 3;
    byte OP_Ack = 4;
    byte OP_Err = 5;

    byte reqType;

    /**
     * Creates a new socket with a random port
     *
     * @param packet - packet from the Server class that receives the packet tand
     * then
     * @throws SocketException
     */
    public TFTPUDPResponder(DatagramPacket packet) throws SocketException {
        inPacket = packet;
        socket = new DatagramSocket(defaultPort); // random port
        socket.setSoTimeout(2000);
    }

    @Override
    public void run() {

        String fileName = "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int sentSoFar = 0; // how much has been sent so far, only used for read
        byte[] write = {};
        int block = 0; // block to be sent with a packet
        int expectedBlock = 0; // block number you are expecting from a packet 

        try {
            // run forever
            Boolean lastPacket = false; // the last packet that needs to be sent
            Boolean allSent = false; // if all the data has been sent from a file
            while (lastPacket == false) {
                address = inPacket.getAddress();
                port = inPacket.getPort();
                byte[] inBuf = inPacket.getData();
                byte[] opCode = {inBuf[0], inBuf[1]};

                if (opCode[1] == OP_RRQ) { // get the file and start sending 
                    fileName = getName(inBuf); // gets the filename out a packet
                    reqType = OP_RRQ;

                    //System.out.println("Read Request");
                    try {
                        write = readContent(fileName); // load in the file to send over
                    } catch (NoSuchFileException n) {
                        sendError("File doesn't exist"); // send an error if it doesn't exist
                        System.err.println("File doesn't exist");
                        lastPacket = true; // no more packets so can finish
                        break;
                    }

                    byte[] blockNumber = incrementBlock(block); // first block, starts with 1 so increment first
                    block++; // increment the field
                    //System.out.println(block);

                    ByteArrayOutputStream portionContent = new ByteArrayOutputStream(); // data that went we sent in this packet

                    int amount = write.length; // amount of bytes needing to be sent
                    //System.out.println("Amount " + amount);
                    int amountLeft = 0;
                    if (((amount - sentSoFar) / 512) >= 1) {// if there is more than 512 then send 512
                        amountLeft = 512;
                    } else { // if less than 512 then send the left over
                        amountLeft = (amount - sentSoFar);
                        //System.out.println("All sent in req");
                    }

                    int sentSoFarInner = sentSoFar; // used so that incrementing inside the loop doesn't cause it to loop forever
                    for (int i = sentSoFar; i < (sentSoFar + amountLeft); i++) { // make sure doesnt fill with empty
                        portionContent.write(write[i]); // write to the bytearray
                        sentSoFarInner++;
                    }
                    sentSoFar = sentSoFarInner; // reassigned back after the loop

                    if (sentSoFar == amount) {
                        allSent = true;
                        //System.out.println("Sent last of the data");
                    }

                    byte[] portion = portionContent.toByteArray(); // convert into a byte array so can go into a packet
                    byte[] data = {0, OP_Data, blockNumber[0], blockNumber[1]};
                    byte[] buf = new byte[data.length + portion.length];

                    System.arraycopy(data, 0, buf, 0, data.length); // copy packet header info into buf
                    System.arraycopy(portion, 0, buf, data.length, portion.length); // copy packet data from file into buf

                    //System.out.println("portion length " + portion.length);
                    outPacket = new DatagramPacket(buf, buf.length, address, port);

                    socket.send(outPacket);
                    //System.out.println("sent read data");

                    if (sentSoFar == amount) {
                        if (amount % 512 == 0) { // this will check if the last packet is 512 if it is then a empty packet needs to be sent
                            blockNumber = incrementBlock(block); // may have to seperate up 
                            block++;
                            byte[] emptyP = {0, OP_Data, blockNumber[0], blockNumber[1]};
                            outPacket = new DatagramPacket(emptyP, emptyP.length, address, inPacket.getPort());
                            socket.send(outPacket);
                            allSent = true;
                            //System.out.println("Sent EMPTY packet of data");
                        }
                    }

                } else if (opCode[1] == OP_Ack) { // start sending data to write
                    if (allSent == true) { // if all sent and recevied last ack can finish
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

                        if (sentSoFar == amount && amount % 512 == 0) { // if last packet to be sent is 512 then send a empty packet 
                            blockNumber = incrementBlock(block); // may have to seperate up 
                            block++;
                            byte[] emptyP = {0, OP_Data, blockNumber[0], blockNumber[1]};
                            outPacket = new DatagramPacket(emptyP, emptyP.length, address, inPacket.getPort());
                            socket.send(outPacket);
                            allSent = true;
                            //System.out.println("Sent EMPTY packet of data");

                        }
                    }
                } else if (opCode[1] == OP_WRQ) { // send back an Ack for data to then be sent
                    fileName = getName(inBuf);
                    reqType = OP_WRQ;
                    //System.out.println("received WRQ");

                    byte[] blockNumber = {0, 0}; //for first req sends a block number of 0 for WRQ
                    byte[] ack = {0, OP_Ack, blockNumber[0], blockNumber[1]}; // sends an ack back meaning that data can then be sent through
                    outPacket = new DatagramPacket(ack, ack.length, address, port);
                    socket.send(outPacket);
                    //System.out.println("Don't send");
                    //do I set a timeout after sending an ack

                } else if (opCode[1] == OP_Data) { // data is being send to write to file should reply with an ack

                    //System.out.println("received data to write");
                    byte[] blockNumber = {inBuf[2], inBuf[3]};
                    byte[] expectedBlockNo = incrementBlock(expectedBlock);// increment block number before it's compared

                    //System.out.println("expected blockNo " + (expectedBlock + 1));
                    //////////////////////// SENDING THE ACK
                    if (blockNumber[0] == expectedBlockNo[0] && blockNumber[1] == expectedBlockNo[1]) {
                        //System.out.println("passed block number");
                        expectedBlock++;

                        //DataOutputStream dos = new DataOutputStream(out); // puts it into out
                        out.write(inPacket.getData(), 4, inPacket.getLength() - 4); // gets the data out of the byte
                        //System.out.println("length of byte array " + out.size());

                        byte[] ack = {0, OP_Ack, blockNumber[0], blockNumber[1]};

                        outPacket = new DatagramPacket(ack, ack.length, address, port);
                        socket.send(outPacket);
                        // may need another timeout to set here
                    } else {
                        //System.out.println("failed block number");
                        //error
                    }
                } else {
                    //System.out.println("None");
                }

                if (inBuf[1] == OP_Data && inPacket.getLength() < 516) { // if lastpacket of data
                    lastPacket = true;
                } else {
                    //inBuf = new byte[516];
                    try {
                        socket.receive(inPacket);
                    } catch (SocketTimeoutException ste) {
                        socket.send(outPacket);
                        //System.out.println("Timeout");
                    }
                }

            } // end of while
        } catch (IOException e) {
            System.err.println(e);
        }

        if (reqType == OP_WRQ) { // writes data to a file if its a write request
            try {
                writeFile(out, fileName);
            } catch (IOException ex) {
                System.err.println(ex);
                //System.out.println("Didn't write file ");
            }
        }

        // Close the socket
        //System.out.println("Socket for this thread is closed");
        socket.close();
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
     * This will get the name of the file out a request packet
     *
     * @param inBuf a requests buffer to extract the name from
     * @return the file name
     * @throws UnsupportedEncodingException
     */
    private String getName(byte[] inBuf) throws UnsupportedEncodingException {
        int lengthofName = 0;
        for (int i = 2; inBuf[i] != (byte) 0; i++) { // count through until you get to a 0 
            lengthofName++;
        }

        byte[] fileName = new byte[lengthofName];

        for (int j = 0; j < lengthofName; j++) { // use the count to 0 to get all the filename
            fileName[j] = inBuf[j + 2];
        }

        String fileNameA = new String(fileName); // make a string
        return fileNameA;
    }

    /**
     * This will send a error packet to the client which will cause the sockets
     * to close
     *
     * @param msg - the msg that should be sent with an error packet
     * @throws IOException
     */
    private void sendError(String msg) throws IOException {
        byte[] msgB = msg.getBytes("US-ASCII"); // send a message with the error
        byte[] errCode = {0, 1};

        byte[] error1 = {0, OP_Err, errCode[0], errCode[1]};

        byte[] error = new byte[error1.length + msgB.length + 1];

        System.arraycopy(error1, 0, error, 0, error1.length); // check this works
        System.arraycopy(msgB, 0, error, error1.length, msgB.length);

        error[error1.length + msgB.length] = (byte) 0;

        DatagramPacket errPacket = new DatagramPacket(error, error.length, address, inPacket.getPort());
        socket.send(errPacket);
    }

    /**
     * Increments block number, turns it into a byte and takes the first 8 bits
     * and puts into a byte array and then takes the send lot of 8 bits and puts
     * into the second cell. It automatically wraps back around to 0.
     *
     * @param blockNo - the block number that needs to be incremented and
     * converted
     * @return byte array of the block number separated up
     */
    public byte[] incrementBlock(int blockNo) {
        blockNo++;
        byte[] b = new byte[2];
        b[0] = (byte) (blockNo & 0xFF); // take first 8 bits
        b[1] = (byte) ((blockNo >> 8) & 0xFF); // shift 8 bits and take that
        return b;
    }

    /**
     * The byte array given to it is written to the file
     *
     * @param bOut - byte array containing the data from the server
     * @param fileName - file to write it to
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeFile(ByteArrayOutputStream out, String fileName) throws FileNotFoundException, IOException {
        //System.out.println("Enter write");
        File file = new File(fileName);
        OutputStream outputStream = new FileOutputStream(fileName);
        outputStream.write(out.toByteArray());

    }

}
