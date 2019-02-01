/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TFTPTCPServer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author chaya
 */
public class TFTPTCPResponder implements Runnable {

    Socket socket = null;
    DataInputStream input; // packet received
    DataOutputStream output; // packet to be sent out

    byte OP_RRQ = 1;
    byte OP_WRQ = 2;
    byte OP_Data = 3;
    byte OP_Err = 5;

    byte[] buf = new byte[516];
    byte[] write = {};
    int block = 0;

    /**
     * Setup the socket this is passed from the server socket to handle a
     * transaction with a client
     *
     * @param clientSocket - the socket that is created by the server socket and
     * passed over
     * @throws SocketException
     */
    public TFTPTCPResponder(Socket clientSocket) throws SocketException, IOException {
        this.socket = clientSocket;

        //socket.setSoTimeout(5000);
        input = new DataInputStream(clientSocket.getInputStream());
        output = new DataOutputStream(clientSocket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            while (input.available() == 0) {
            }

            //System.out.println("amount in input stream " + input.available());
            buf = new byte[input.available()];
            input.read(buf);

            byte op = buf[1];
            String file = getName(buf);

            if (op == OP_WRQ) {
                //System.out.println("WRQ");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                boolean lastPacket = false;
                while (lastPacket == false) {
                    while (input.available() == 0) {

                    }
                    //System.out.println("Amount of data " + input.available());
                    if (input.available() > 0) {
                        //System.out.println("data");
                        buf = new byte[input.available()];
                        if (input.available() >= 516) {
                            buf = new byte[516];
                            input.read(buf, 0, 516);
                        } else {
                            buf = new byte[input.available()];
                            input.read(buf);
                        }

                        out.write(buf, 4, buf.length - 4);
                        if (buf.length < 516) {
                            lastPacket = true;
                        }

                    }
                }
                writeFile(out, file);

            } else {
                //System.out.println("RRQ");

                int sentSoFar = 0; // used if writing a file to server
                boolean allSent = false; //  sent all the data and waiting for last ack 

                try {
                    write = readContent(file);
                } catch (NoSuchFileException e) {
                    output.write(sendError("File doesn't exist"));
                    System.err.println("File doesn't exist");
                    allSent = true;
                }

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
            }
        } catch (IOException ex) {
            Logger.getLogger(TFTPTCPResponder.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            socket.close();
            input.close();
            output.close();
        } catch (IOException ex) {
            Logger.getLogger(TFTPTCPResponder.class.getName()).log(Level.SEVERE, null, ex);
        }

        //System.out.println("FINISHED" + "\n[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]\n");

        //System.out.println("FINISHED" + "\n[[[[[[[[[[[[[[[[[[[[[]]]]]]]]]]]]]]]]]]]]\n");
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
        for (int i = 2; inBuf[i] != (byte) 0; i++) {
            lengthofName++;
        }

        byte[] fileName = new byte[lengthofName];

        for (int j = 0; j < lengthofName; j++) {
            fileName[j] = inBuf[j + 2];
        }
        String fileNameA = new String(fileName);
        return fileNameA;
    }

    /**
     * This will send a error packet to the client which will cause the sockets
     * to close
     *
     * @param msg - the msg that should be sent with an error packet
     * @throws IOException
     */
    private byte[] sendError(String msg) throws IOException {
        byte[] msgB = msg.getBytes("US-ASCII");
        byte[] errCode = {0, 1};

        byte[] error1 = {0, OP_Err, errCode[0], errCode[1]};

        byte[] error = new byte[error1.length + msgB.length + 1];

        System.arraycopy(error1, 0, error, 0, error1.length); // check this works
        System.arraycopy(msgB, 0, error, error1.length, msgB.length);

        error[error1.length + msgB.length] = (byte) 0;

        return error;
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
