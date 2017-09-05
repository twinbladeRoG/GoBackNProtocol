import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * Client
 * @author Sohan Dutta
 */
public class FTPClient {

    public  byte[] MESSAGE_START = { 0x52, 0x44, 0x54 };
    public  byte[] MESSAGE_END = { 0x45, 0x4e, 0x44, 0x0a, 0x0d };
    public  byte[] CRLF = new byte[] { 0x0a, 0x0d };
    public  byte[] REQUEST = new byte[] {0x52, 0x45, 0x51, 0x55, 0x45, 0x53, 0x54};
    public  byte[] ACK = new byte[] {0x41, 0x43, 0x4b};

    public  int MSG_FRONT = 4;
    public  int MSG_BACK = 2;
    public  int MSG_LAST = 5;
    public  int CONSIGNMENT = 512;


    public static void main(String[] args)
    {
      FTPClient c = new FTPClient();
      c.run(args);
    }

    public void run (String args[]) {
      if(args.length < 3) {
          System.out.println("Missing arguments");
          return;
      }
      int forgetACK = args.length - 3;
      int[] Ack_to_forget = null;
      if(forgetACK > 0) {
          Ack_to_forget = new int[forgetACK];
          for(int i = 0; i < forgetACK; i++) {
              Ack_to_forget[i] = Integer.parseInt(args[i+3]);
          }
          Arrays.sort(Ack_to_forget);
      }
      int curr = 0;
      DatagramSocket ds = null;
      InetAddress ip;
      int port = Integer.parseInt(args[1]);
      String getFileName = args[2];
      String writeFile = "Copy_" + getFileName;
      byte[] fileName = getFileName.getBytes();
      byte[] FIRST_MSG = getByteStream(REQUEST, fileName, CRLF);
      FileOutputStream myFOS;
      DatagramPacket rp, sp;
      byte[] rd = new byte[CONSIGNMENT + MSG_FRONT + MSG_BACK];
      byte[] sd;
      try {
          ip = InetAddress.getByName(args[0]);
          System.out.println("Requesting " + getFileName + " from " + ip + " port " + port);
          ds = new DatagramSocket();
          sp = new DatagramPacket(FIRST_MSG, FIRST_MSG.length, ip, port);
          ds.send(sp);
          rp = new DatagramPacket(rd, rd.length);
          int Rn = 0, window;
          myFOS = new FileOutputStream(new File(writeFile));
          boolean fileReceieved = false;
          while(!fileReceieved) {
              System.out.println("");
              ds.receive(rp);
              byte[] msg = rp.getData();
              String s = new String(msg, MESSAGE_START.length, 1);
              int sendFr = strngConvert(s);

              if(sendFr == Rn) {
                  int msgLength = matchByteSequence(msg, MESSAGE_END);
                  if (msgLength == msg.length)
                  {
                      myFOS.write(msg, MSG_FRONT, msg.length-MSG_FRONT-MSG_BACK);
                      System.out.println("Received CONSIGNMENT " + sendFr);
                      Rn = (Rn + 1)%128;
                      sd = makeACK(Rn);
                      sp = new DatagramPacket(sd, sd.length, ip, port);
                      if(sendACK(forgetACK, Ack_to_forget, curr, Rn)) {
                          ds.send(sp);
                          System.out.println("Sent ACK " + Rn);
                      }
                      else {
                          curr++;
                          System.out.println("Forgot ACK " + Rn);
                      }

                  } else {
                      myFOS.write(msg, MSG_FRONT, msgLength-MSG_FRONT-MSG_LAST);
                      System.out.println("Received CONSIGNMENT " + Rn);
                      Rn = (Rn + 1)%128;
                      sd = makeACK(Rn);
                      sp = new DatagramPacket(sd, sd.length, ip, port);
                      ds.send(sp);
                      System.out.println("END");
                      myFOS.close();
                      fileReceieved = true;
                      break;
                  }
              }
              else if (sendFr < Rn){
                  System.out.println("Received CONSIGNMENT " + sendFr + " duplicate - discarding");
                  sd = makeACK(Rn);
                  sp = new DatagramPacket(sd, sd.length, ip, port);
                  ds.send(sp);
                  System.out.println("Sent ACK " + Rn);
              }
              else {
                  System.out.println("Received CONSIGNMENT " + sendFr + " incorrect consignment - discarding");
              }
          }
      }
      catch (SocketException e)
      {
          System.out.println("SOCKET issues");
      }
      catch (UnknownHostException e)
      {
          System.out.println("HOST issues");
      }
      catch (IOException e)
      {
          System.out.println("IO Exception occured");
      }
    }

    public  boolean sendACK(int n, int[] f, int curr, int Rn)
    {
        boolean result;
        if(n == 0) result = true;
        else if(curr >= n) result = true;
        else if(f[curr] != Rn)  result = true;
        else result = false;
        return result;
    }


    public  int strngConvert(String str) {
        int c = str.charAt(0);
        return c;
    }

     public  byte[] makeACK(int Rn) {
        byte[] frame;

        byte[] SEQ_NO = new byte[] { (byte)Rn };
        frame = getByteStream(ACK, SEQ_NO,CRLF);
        return frame;
    }

     public  byte[] getByteStream(byte[] a, byte[] b, byte[] c)
    {
        byte[] result = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length+b.length, c.length);
        return result;
    }
    public   int matchByteSequence(byte[] inp, byte[] ref1) {
        int j = 0, x;
        for(x = 0; x <inp.length; x++) {
            if(inp[x] == ref1[j]) {
                j++;
            }
            else {
                j = 0;
                if(inp[x] == ref1[j]) {
                    j++;
                }
            }
            if(j == ref1.length) {
                x++;
                break;
            }
        }
        return x;
    }

}
