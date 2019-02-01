/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPTCPClient;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
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
public class TFTPTCPClient {

    InetAddress address;
    final int packetSize = 516;

    byte OP_RRQ = 1;
    byte OP_WRQ = 2;
    byte OP_Data = 3;
    byte OP_Err = 5;

    Socket socket;
    Random r = new Random();
    int sourcePort = r.nextInt(65535 - 1024) + 1024; // random port
    int destPort = 69; // main server port

    DataInputStream input;
    DataOutputStream output;

    byte[] write = {};
    byte[] buf;

    int block = 0;

    // the client will take the IP Address of the server (in dotted decimal format as an argument)
    // given that for this tutorial both the client and the server will run on the same machine, you can use the loopback address 127.0.0.1
    /**
     * The arugements passed to this should be first the ip address, then 1 for
     * a read rquest or 2 for a write request , and finally a filename to write
     * to the server or a file to write the read request into.
     *
     * @param args - command line
     * @throws IOException
     * @throws SocketException
     * @throws Exception
     */
    public static void main(String[] args) throws IOException, SocketException, Exception {
        if (args.length <= 2) {
            System.out.println("Missing parameters give in the order IP, Request type (1 or 2), Filename");
            return;
        }

        //for(int i=0;i<512;i++){
        //   System.out.println("1");
        //}
        String fileName = args[2];
        TFTPTCPClient client = new TFTPTCPClient();
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
        socket = new Socket(address, 69);
        String file = args[2]; //e.g. abc.txt
        //socket.setSoTimeout(5000);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());

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
                System.err.println("Socket closed");
                System.exit(0);
            }
        } else {
            System.err.println("Not a valid command. Enter 1 for read and 2 for write.");
            System.exit(0); // exit as can't proceed with no correct command 
        }

        ByteArrayOutputStream reqByteArray = new ByteArrayOutputStream();
        reqByteArray.write(createRequest(op, fileName)); // puts the bytes in that are needed for a intital request
        byte[] req = reqByteArray.toByteArray(); // and then is put into a byte array

        output.write(req); // writes packet to output stream where server can collect it 
        //System.out.println(output.size());
        //System.out.println("wait");

        if (op == OP_WRQ) {
            //System.out.println("WRQ");
            try {
                write = readContent(file);
            } catch (NoSuchFileException e) {
                System.err.println("Can't find file");
                System.exit(0);
            }

            int sentSoFar = 0; // used if writing a file to server
            boolean allSent = false; //  sent all the data and waiting for last ack 

            while (allSent == false) {
                ByteArrayOutputStream sendToWrite = new ByteArrayOutputStream();
                byte[] blockNumber = incrementBlock(block); // may have to seperate up 
                block++;
                //System.out.println("blockNo sent " + block);

                //send 512 bytes of what there is to write
                int amount = write.length;
                //System.out.println(amount);
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

                //for(byte dataa : data){
                //  System.out.println(dataa);
                //}
                //System.out.println("data buffer " + data.length);
                //System.out.println("output " + output.size());
                //Thread.sleep(1);
                output.write(data);
                output.flush();
                //System.out.println("output " + output.size());
                //System.out.println("send data to write");
                //System.out.println("------------------====================----------------------");

                if (sentSoFar == amount && amount % 512 == 0) { // this will check if the last packet is 512 if it is then a empty packet needs to be sent
                    blockNumber = incrementBlock(block);
                    block++;

                    byte[] emptyP = {0, OP_Data, blockNumber[0], blockNumber[1]};

                    output.write(emptyP);
                    output.flush();
                    allSent = true;
                    //System.out.println("Sent EMPTY packet of data");
                }
            }
        } else {

            //System.out.println("RRQ");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean lastPacket = false;
            boolean error = false;
            while (lastPacket == false && error == false) {
                while (input.available() == 0) { // wait for data
                }
                int i = input.available();
                //System.out.println("Amount of data " + i);               
                if (i > 0) {
                    //System.out.println("data");
                    if (i >= 516) { // only takes 516 or less bytes out at a time
                        buf = new byte[516];
                        input.read(buf, 0, 516);
                    } else {
                        buf = new byte[i];
                        input.read(buf);
                    }

                    if (buf[1] == OP_Err) {
                        System.err.println("Error");
                        error = true;
                    } else {
                        out.write(buf, 4, buf.length - 4);
                        if (buf.length < 516) {
                            lastPacket = true;
                        }
                    }
                }
            }
            if (error == false) {
                writeFile(out, file);
            }
        }

        socket.close();
        input.close();
        output.close();
        //System.out.println("FINISHED");
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
        rrqBytes[pointer] = (byte) 0;
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
        b[0] = (byte) (blockNo & 0xFF);
        b[1] = (byte) ((blockNo >> 8) & 0xFF);
        return b;
    }
}
