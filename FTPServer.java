import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * Server
 * @author Sohan Dutta
 */
public class FTPServer {

    public  byte[] RDT = new byte[] { 0x52, 0x44, 0x54 };
    public  byte[] END = new byte[] { 0x45, 0x4e, 0x44 };
    public  byte[] CRLF = new byte[] { 0x0a, 0x0d };
    public  byte[] REQ = new byte[] {0x52, 0x45, 0x51, 0x55, 0x45, 0x53, 0x54};
    public  byte[] ACK = new byte[] {0x41, 0x43, 0x4b};
    public  int CONSIGNMENT = 512;

    public  int windowSize = 4;
    public  byte[][] buffer = new byte[windowSize][];

    public static void main(String[] args)
    {
        FTPServer s = new FTPServer();
        s.run(args);
    }


    private  byte[] getByteStream(byte[] a, byte[] b, byte[] c, byte[] d) {
        byte[] result = new byte[a.length + b.length + c.length + d.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length+b.length, c.length);
        System.arraycopy(d, 0, result, a.length+b.length+c.length, d.length);
        return result;
    }

    public void run(String args[]) {
      if(args.length < 1) {
          System.out.println("INCORRECT format. Need more arguments");
          return;
      }

      int framesToForget = args.length - 1;

      int[] toForgetCon = null;

      if(framesToForget > 0) {
          toForgetCon = new int[framesToForget];
          for(int i = 0; i < framesToForget; i++) {
              toForgetCon[i] = Integer.parseInt(args[i+1]);
          }
          Arrays.sort(toForgetCon);
      }
      int seqNo = 0, Sf = 0, Sl;
      int curr = 0;
      int sPort = Integer.parseInt(args[0]);
      byte[] myData = new byte[CONSIGNMENT];
      byte[] request = new byte[30];
      byte[] ack = new byte[10];
      int bytesRead = 0;
      DatagramSocket ds = null;
      DatagramPacket rp, sp;
      InetAddress ip;
      int cPort;
      try {
          ds = new DatagramSocket(sPort);
          rp = new DatagramPacket(request, request.length);
          boolean fileSent = false;
          ds.receive(rp);
          ip = rp.getAddress(); cPort = rp.getPort();
          request = rp.getData();
          String fileName = extractFileName(request);
          System.out.println("Receieved request from ip: " + ip + " for file: " + fileName);
          FileInputStream myFIS = new FileInputStream(fileName);
          bytesRead = myFIS.read(myData);
          while (!fileSent) {

              Sl = seqNo + windowSize;
              while(bytesRead > -1 && seqNo < Sl) {
                  byte[] frame = makeFrame(seqNo, myData, bytesRead);
                  int count = seqNo%4;
                  buffer[count] = frame;
                  seqNo++;
                  bytesRead = myFIS.read(myData);
              }

              do {
                  for(int i = Sf; i <  seqNo; i++) {
                      int count = i%4;
                      byte[] frameToSend = buffer[count];
                      sp = new DatagramPacket(frameToSend, frameToSend.length, ip, cPort);
                      if(sendFrame(framesToForget, toForgetCon, curr, i)) {
                          ds.send(sp);
                          System.out.println("Sent CONSIGNMENT " + i);
                      }
                      else {
                          curr++;
                          System.out.println("Forgot CONSIGNMENT " + i);
                      }
                  }
                  System.out.println("");
                  //receive ACK
                  ds.setSoTimeout(30);
                  try {
                      while(Sf < seqNo) {
                          rp = new DatagramPacket(ack, ack.length);
                          ds.receive(rp);
                          ack = rp.getData();
                          int ackN = ack[3];
                          if(ackN <= seqNo && ackN > Sf) {
                              Sf = ackN;
                              if(!(bytesRead == -1 && ackN == seqNo))
                                  System.out.println("Received ACK " + ackN);
                              else System.out.println("File Received by client");

                          }
                      }
                  }
                  catch(SocketTimeoutException e) {
                      System.out.println("Timeout");
                  }
              }while(Sf < seqNo);
              System.out.println("");
              if(Sf == seqNo && bytesRead !=-1)
                  System.out.println("Shift Window!!");
              if(bytesRead == -1) {
                  System.out.println("END");
                  fileSent = true;
              }
          }
      }
      catch (FileNotFoundException ex1)
      {
          System.out.println("FILE NOT FOUND");
      }
      catch (SocketException e)
      {
          System.out.println("SOCKET ISSUE");
      }
      catch(IOException ex)
      {
          System.out.println(ex.getMessage());
      }
    }

    private  byte[] getByteStream(byte[] a, byte[] b, byte[] c, byte[] d, byte[] e) {
        byte[] result = new byte[a.length + b.length + c.length + d.length + e.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length+b.length, c.length);
        System.arraycopy(d, 0, result, a.length+b.length+c.length, d.length);
        System.arraycopy(e, 0, result, a.length+b.length+c.length+d.length, e.length);
        return result;
    }

    public byte[] makeFrame(int seqNo, byte[] data, int bytesRead) {
        byte[] frame;
        byte[] SEQ_NO = new byte[] { (byte)seqNo };
        if(bytesRead < CONSIGNMENT) {
            byte[] lastBlock = new byte[bytesRead];
            System.arraycopy(data, 0, lastBlock, 0, bytesRead);
            frame = getByteStream(RDT, SEQ_NO, lastBlock, END, CRLF);
        }
        else {
            frame = getByteStream(RDT, SEQ_NO, data, CRLF);
        }
        return frame;
    }

    public  int str2Int(String str) {
        int c = str.charAt(0);
        return c;
    }

    public  String extractFileName(byte[] request) {
        byte[] fileName = new byte[request.length - REQ.length - CRLF.length];
        int j = 0;
        for(int i = REQ.length; i < request.length - CRLF.length; i++) {
            if(request[i] == 10)
                break;
            fileName[i - REQ.length] = request[i];
            j++;
        }
        String filename = new String(fileName, 0, j);
        return filename;
    }

    public  boolean sendFrame(int n, int[] f, int curr, int Rn)
    {
        boolean res;
        if(n == 0)
            res = true;
        else if(curr >= n)
            res = true;
        else if(f[curr] != Rn)
            res = true;
        else
            res = false;
        return res;
    }

}
