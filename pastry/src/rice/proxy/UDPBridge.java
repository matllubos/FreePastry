package rice.proxy;

import java.io.*;
import java.net.*;
import com.mindbright.ssh2.*;
import com.mindbright.util.*;

public class UDPBridge extends Thread {
  
  public static int DATAGRAM_LENGTH = 65536;
  
  protected int udpPort;
  protected int tcpPort;
  protected boolean direction;
  
  public UDPBridge(int udpPort, int tcpPort, boolean direction) {
    super("UDP Bridge Thread");
    
    this.udpPort = udpPort;
    this.tcpPort = tcpPort;
    this.direction = direction;
    
    debug("Running with UDP port " + udpPort + " and TCP port " + tcpPort + " in " + (direction ? "input" : "output") + " mode.");
  }
  
  public void run() {
    try {
      DatagramPacket packet = new DatagramPacket(new byte[DATAGRAM_LENGTH], DATAGRAM_LENGTH);
      
      if (direction) {
        DatagramSocket listen = new DatagramSocket(udpPort);
        debug("Listening on UDP port " + udpPort);
        
        DataOutputStream out = new DataOutputStream((new Socket("localhost", tcpPort)).getOutputStream());
        debug("Established socket to TCP port " + tcpPort);
        
        
        while (true) {
          listen.receive(packet);
          debug("Received packet of length " + packet.getLength());
          
          out.writeInt(packet.getLength());
          out.write(packet.getData(), 0, packet.getLength());
          debug("Done forwarding packet");
        }
      } else {
        packet.setAddress(InetAddress.getLocalHost());
        packet.setPort(udpPort);
        
        ServerSocket listen = new ServerSocket(tcpPort);
        debug("Listening on TCP port " + tcpPort);
        
        while (true) {
          final DataInputStream read = new DataInputStream(listen.accept().getInputStream());
          
          Thread t = new Thread("Connection Thread") {
            public void run() {
              try {
                debug("Accepted connection on TCP port " + tcpPort);
                byte[] BUFFER = new byte[DATAGRAM_LENGTH];
                DatagramPacket packet = new DatagramPacket(new byte[DATAGRAM_LENGTH], DATAGRAM_LENGTH);
                packet.setAddress(InetAddress.getLocalHost());
                packet.setPort(udpPort);
                
                DatagramSocket write = new DatagramSocket();
                
                while (true) {
                  int length = read.readInt();
                  debug("Read incoming packet of length " + length);
                  
                  read.readFully(BUFFER, 0, length);
                  debug("Read incoming data of length " + length);
                  
                  packet.setLength(length);
                  packet.setData(BUFFER, 0, length);
                  write.send(packet);
                  debug("Done forwarding packet");
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          };
          
          t.start();
        }
      }
    } catch (IOException e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    } 
  }
  
  public void debug(String message) {
    System.out.println("DEBUG: " + message);
  }
  
  public static void main(String[] args) {
    boolean input = true;
    int udpPort = 30000;
    int tcpPort = 30001;
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-output")) {
        input = false;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-UDPport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) udpPort = n;
        break;
      }
    }
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-TCPport") && i+1 < args.length) {
        int n = Integer.parseInt(args[i+1]);
        if (n > 0) tcpPort = n;
        break;
      }
    }
    
    UDPBridge bridge = new UDPBridge(udpPort, tcpPort, input);
    bridge.start();
  }
  
}