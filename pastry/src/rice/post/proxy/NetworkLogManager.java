package rice.post.proxy;

import java.io.*;
import java.net.*;
import java.util.zip.*;
import rice.proxy.*;
import java.security.*;
import rice.post.security.*;
import rice.p2p.util.*;

public class NetworkLogManager extends LogManager {
  
  protected File file;
  
  protected String[] other;
  
  protected FileOutputStream fos;
  
  protected long interval;
  
  protected long start;
  
  protected int pastry_port;
  
  protected int buffer_size;
  
  protected NetworkLogManagerThread thread;
  
  public NetworkLogManager(Parameters parameters) {
    this.file = new File(parameters.getStringParameter("standard_output_redirect_filename"));
    this.other = parameters.getStringArrayParameter("standard_output_network_other_filenames");
    this.interval = parameters.getLongParameter("standard_output_network_interval");
    this.buffer_size = parameters.getIntParameter("standard_output_network_buffer_size");
    this.pastry_port = 10001;
    
    if (file.exists()) 
      file.renameTo(getFileName());
    
    try {
      this.fos = new FileOutputStream(file);
    } catch (IOException e) {
      System.err.println("ERROR: problem with file " + file + " - got exception " + e);
    }
        
    this.start = System.currentTimeMillis();
  }
  
  protected void setInfo(int port, PublicKey key, InetSocketAddress server) {
    this.pastry_port = port;
    this.thread = new NetworkLogManagerThread(key, server);
    this.thread.start();
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
    
    protected PublicKey key;
    
    public NetworkLogManagerThread(PublicKey key, InetSocketAddress server) {      
      this.host = server;
      this.key = key;
      
      this.buffer = new byte[buffer_size];
    }
    
    public void run() {
      try {
        Thread.sleep(30000);
      } catch (InterruptedException e) {}
      
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
        sendFile(files[i], true);
      
      files = new File(".").listFiles(new FilenameFilter() {
        public boolean accept(File parent, String file) {
          for (int i=0; i<other.length; i++)
            if (file.startsWith(other[i]))
              return true;
          
          return false;
        }
      });
      
      for (int i=0; i<files.length; i++) 
        sendFile(files[i], false);
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
    
    protected void sendFile(File file, boolean delete) {
      Socket socket = new Socket();
      InputStream in = null;
      GZIPOutputStream out = null;

      try {
        in = new FileInputStream(file);

        socket.connect(host);      
        out = new GZIPOutputStream(new EncryptedOutputStream(key, socket.getOutputStream()));
        
        byte[] header = getHeader(file);
        
        System.out.println("Writing length " + header.length);
        
        out.write(MathUtils.longToByteArray(header.length));
        out.write(header);

        int read = 0;
        
        while ((read = in.read(buffer)) != -1) 
          out.write(buffer, 0, read); 
        
        out.finish();
        out.flush();
        System.out.println("Done writing...");
        
        int done = socket.getInputStream().read(new byte[1]);
        System.out.println("Done reading... (" + done + ")");
        
        if (done < 0)
          throw new IOException("Log file was not correctly received - skipping...");
      } catch (IOException e) {
        System.err.println("ERROR: Got exception " + e + " while sending file - aborting!");
        e.printStackTrace();
        return;
      } finally {
        try {
          if (socket != null) socket.close();
        } catch (IOException e) {
          System.err.println("PANIC: Got exception " + e + " while closing streams!");
        }
      }
      
      if (delete)
        file.delete();
    }
  }
}
