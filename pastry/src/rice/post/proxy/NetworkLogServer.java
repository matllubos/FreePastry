package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;

public class NetworkLogServer {
  
  protected int port;
  
  protected int count;
  
  public NetworkLogServer(int port) {
    this.port = port;
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
  
  public static void main(String[] args) {
    new NetworkLogServer(Integer.parseInt(args[0])).start();
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
    
    protected void readHeader(InputStream in) {
      try {
        byte[] dumb = new byte[8];
        
        int b = 0;

        while (b < 8) {
          int i = in.read(dumb, b, 8-b);
          if (i <= 0)
            throw new EOFException();
          else
            b += i;
          
          System.out.println(id + " b is " + b);
        }
        
        int len = (int) SecurityUtils.getLong(dumb);
        
        System.out.println(id + " Read length " + len);
        byte[] header = new byte[len];
        
        int c = 0;
 
        while (c < len) {
          int i = in.read(header, c, len-c);
          if (i <= 0)
            throw new EOFException();
          else
            c += i;
        }
        
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
      InputStream in = null;
      
      try {
        in = socket.getInputStream();
        readHeader(in);
        
        int total = 0;
        int read = 0;
        
        in = new GZIPInputStream(in);
        
        while ((total < length) && ((read = in.read(buffer)) > 0)) {
          out.write(buffer, 0, read); 
          total += read;
          
      //    System.out.println(id + " Read " + total + " of " + length + " bytes...");
        }
        
        System.out.println(id + " Done reading, sending confirmation...");
        
        if (read != -1) socket.getOutputStream().write((byte) 1); 
        System.out.println(id + " Done writing...");
      } catch (IOException e) {
        System.err.println("ERROR: Got exception " + e + " while reading file - aborting!");
      } finally {
        try {
          System.out.println(id + " Exiting");
          
          in.close();
          if (out != null) out.close();
          if (out != null) socket.close();
        } catch (IOException e) {
          System.err.println("PANIC: Got exception " + e + " while closing streams!");
        }
      }
    }
  }
}
