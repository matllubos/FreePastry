package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;

public class ConnectivityCheckClient {
  
  protected InetSocketAddress server;
  
  protected InetAddress local;
  
  protected ConnectivityCheckClientClient client;
  
  public ConnectivityCheckClient(InetSocketAddress server) {
    this.server = server;
  }
  
  public static void main(String[] args) throws Exception {
    ConnectivityCheckClient c = new ConnectivityCheckClient(new InetSocketAddress(args[0], Integer.parseInt(args[1])));
    System.out.println("Checking ...");
    System.out.println("" + c.check(30000, 10001));
  }
  
  public InetAddress check(long timeout, int port) throws Exception {
     client = new ConnectivityCheckClientClient(server, port);

    synchronized (this) {
      client.start();
      
      System.out.println("Sleeping for " + timeout);
      this.wait(timeout);
    }
    
    System.out.println("Awake " + local);
    
    client.die();
    
    return local;
  }
  
  public String getError() {
    if ((! client.tcp) && (! client.udp))
      return "Received no response on either TCP or UDP.";
    else if (! client.tcp)
      return "Received no response on TCP.";
    else if (! client.tcp)
      return "Received no response on UDP.";
    else 
      return "Unknown error.";
  }
  
  protected class ConnectivityCheckClientClient extends Thread {
    
    protected InetSocketAddress server;
    
    protected int port;
    
    protected ServerSocket socket;
    
    protected DatagramSocket dsocket;
    
    protected boolean udp;
    
    protected boolean tcp;
    
    public ConnectivityCheckClientClient(InetSocketAddress address, int port) {      
      this.server = address;
      this.port = port;
    }
    
    public void run() {
      try {
        
        System.out.println("Bound to TCP/UDP port " + port);
        socket = new ServerSocket(port);
        dsocket = new DatagramSocket(port);
        
        send();
        
        System.out.println("Sent UDP request ");
        
        DatagramPacket packet = new DatagramPacket(new byte[32000], 32000);
        dsocket.receive(packet);
        
        udp = true;
        
        System.out.println("Received UDP response ");
        
        Socket s = socket.accept();
        
        ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
        
        local = ((InetSocketAddress) ois.readObject()).getAddress();
          
        tcp = true;
        
        System.out.println("Sent TCP response " + local);
        
        synchronized (ConnectivityCheckClient.this) {
          ConnectivityCheckClient.this.notifyAll();
        }
      } catch (Exception e) {
        System.out.println("Got error " + e + " communicating with address " + server);
      }
    }
    
    protected void die() throws IOException {
      socket.close();
      dsocket.close();
    }
    
    protected void send() throws IOException {
      DatagramSocket datagram = new DatagramSocket();
      datagram.connect(server);
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      
      oos.writeInt(port);
      oos.close();
      
      DatagramPacket packet = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length);
      datagram.send(packet);
      
      datagram.close();
    }
      
  }
}
