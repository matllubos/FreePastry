package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;
import java.security.*;
import rice.p2p.util.*;
import rice.p2p.multiring.*;

public class NetworkLogServer {
  
  protected int port;
  
  protected int count;
  
  protected PrivateKey key;
  
  public NetworkLogServer(PrivateKey key, int port) {
    this.port = port;
    this.key = key;
    this.count = 0;
  }
  
  public void start() {
    try {
      ServerSocket server = new ServerSocket(port);
      
      while (true) 
        new NetworkLogClient(server.accept(), count++).start();
    } catch (Exception e) {
      System.err.println("Got error " + e);
    }
  }
  
  public static void main(String[] args) throws Exception {
    // add cert stuff here
    String ring = args[0];
    String pass = args[1];
    KeyPair pair = RingCertificate.readKeyPair(ring.toLowerCase(), pass);
    new NetworkLogServer(pair.getPrivate(), Integer.parseInt(args[2])).start();
  }
    
  protected class NetworkLogClient extends Thread {
    
    protected Socket socket;
    
    protected OutputStream out;
    
    protected long length;
    
    protected byte[] buffer = new byte[256000];
    
    protected int id;
    
    public NetworkLogClient(Socket socket, int id) {      
      this.socket = socket;
      this.id = id;
    }
    
    protected void readHeader(DataInputStream in) {
      try {
        int len = (int) in.readLong();
        
        System.out.println(id + " Read length " + len);
        byte[] header = new byte[len];
        in.readFully(header);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(header);
        ObjectInputStream ois = new ObjectInputStream(bais);
        
        String filename = ((String) ois.readObject()).replaceAll("/", "-");
        
        System.out.println(id + " Writing to file " + filename);
        
        out = new GZIPOutputStream(new FileOutputStream(new File(".", filename + ".gz")));
        length = ois.readLong();
        ois.close();
      } catch (Exception e) {
        System.out.println(id + " ERROR: Could not read header... " + e);
        throw new RuntimeException(e);
      }
    }
    
    public void run() {
      DataInputStream in = null;
      
      try {
        in = new DataInputStream(new GZIPInputStream(new EncryptedInputStream(key, socket.getInputStream())));
        readHeader(in);
        
        int total = 0;
        int read = 0;
        
        while ((total < length) && ((read = in.read(buffer)) > 0)) {
          out.write(buffer, 0, read); 
          total += read;
        }
        
        System.out.println(id + " Done reading, sending confirmation...");
        
        if (read != -1) socket.getOutputStream().write((byte) 1); 
        System.out.println(id + " Done writing... (" + read + ")");
      } catch (IOException e) {
        System.err.println("ERROR: Got exception " + e + " while reading file - aborting!");
        e.printStackTrace();
      } finally {
        try {
          System.out.println(id + " Exiting");
          
          if (in != null) in.close();
          if (out != null) out.close();
          if (socket != null) socket.close();
        } catch (IOException e) {
          System.err.println("PANIC: Got exception " + e + " while closing streams!");
        }
      }
    }
  }
}
