package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import rice.post.security.*;

public class NetworkLogManager extends LogManager {
  
  protected File file;
  
  protected FileOutputStream fos;
  
  protected long interval;
  
  protected long start;
  
  protected int pastry_port;
  
  protected NetworkLogManagerThread thread;
  
  public NetworkLogManager(Parameters parameters) {
    this.file = new File(parameters.getStringParameter("standard_output_redirect_filename"));
    this.interval = parameters.getLongParameter("standard_output_network_interval");
    this.pastry_port = parameters.getIntParameter("pastry_port");
    
    if (file.exists()) 
      file.renameTo(getFileName());
    
    try {
      this.fos = new FileOutputStream(file);
    } catch (IOException e) {
      System.err.println("ERROR: problem with file " + file + " - got exception " + e);
    }
    
    this.thread = new NetworkLogManagerThread(parameters);
    this.thread.start();
    
    this.start = System.currentTimeMillis();
  }
  
  protected File getFileName() {
    return new File(this.file.getName() + "." + System.currentTimeMillis());
  }
  
  public void write(int b) throws IOException {
    check();
    fos.write(b);
  }
  
  public void write(byte b[]) throws IOException {
    check();
    fos.write(b);
  }
  
  public void write(byte b[], int off, int len) throws IOException {
    check();
    fos.write(b, off, len);
  }
  
  public void flush() throws IOException {
    fos.flush();
  }
  
  public void close() throws IOException {
    fos.close();
  }
  
  protected synchronized void check() {
    if (this.start+this.interval > System.currentTimeMillis()) 
      return;
    
    try {
      this.fos.close();
      this.file.renameTo(getFileName());
      this.start = System.currentTimeMillis();
      
      this.fos = new FileOutputStream(file);
    } catch (IOException e) {
      System.err.println("ERROR: Got exception " + e + " while switching logs.");
    }
  }
  
  protected class NetworkLogManagerThread extends Thread {
    
    protected InetSocketAddress host;
    
    protected byte[] buffer;
    
    public NetworkLogManagerThread(Parameters parameters) {      
      this.host = new InetSocketAddress(parameters.getStringParameter("standard_output_network_host_name"),
                                        parameters.getIntParameter("standard_output_network_host_port"));
      
      this.buffer = new byte[parameters.getIntParameter("standard_output_network_buffer_size")];
    }
    
    public void run() {
      while (true) {
        System.out.println("Waking up...");
        sendFiles(); 
        sleep();
      }
    }
    
    protected void sleep() {
      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        System.err.println("Caught IE " + e + " on sleep - stopping!");
        throw new RuntimeException("Unexpected InterruptedException");
      }
    }
    
    protected void sendFiles() {
      File[] files = new File(".").listFiles(new FilenameFilter() {
        public boolean accept(File parent, String file) {
          return file.startsWith(NetworkLogManager.this.file + ".");
        }
      });
      
      for (int i=0; i<files.length; i++) 
        sendFile(files[i]);
    }
    
    protected byte[] getHeader(File file) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        
        oos.writeObject(InetAddress.getLocalHost().getHostAddress() + ":" + pastry_port + "." + file.getName());
        oos.writeLong(file.length());
        oos.close();
        return baos.toByteArray();
      } catch (Exception e) {
        System.out.println("ERROR: Could not create header... " + e);
        throw new RuntimeException(e);
      }
    }
    
    protected void sendFile(File file) {
      Socket socket = new Socket();
      InputStream in = null;
      OutputStream out = null;

      try {
        in = new FileInputStream(file);

        socket.connect(host);        
        out = socket.getOutputStream();
        
        byte[] header = getHeader(file);
        
        System.out.println("Writing length " + header.length);
        
        out.write(SecurityUtils.getByteArray((long) header.length));
        out.write(header);
        out.flush();
        
        out = new GZIPOutputStream(out);
        
        int read = 0;
        
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read); 
        //  System.out.println("Wrote " + read + " more bytes");
        }
        
        ((GZIPOutputStream) out).finish();
        System.out.println("Done writing...");
        
        int done = socket.getInputStream().read(new byte[1]);
        System.out.println("Done reading...");
      } catch (IOException e) {
        System.err.println("ERROR: Got exception " + e + " while sending file - aborting!");
        return;
      } finally {
        try {
          if (in != null) in.close();
          if (out != null) out.close();
          if (socket != null) socket.close();
        } catch (IOException e) {
          System.err.println("PANIC: Got exception " + e + " while closing streams!");
        }
      }
      
      file.delete();
    }
  }
}
