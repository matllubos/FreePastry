/*
 * Created on Jun 28, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package rice.post.proxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PublicKey;
import java.util.zip.GZIPOutputStream;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.p2p.util.EncryptedOutputStream;
import rice.p2p.util.MathUtils;
import rice.selector.TimerTask;

/**
 * @author jstewart
 *
 */
public class NetworkLogUploadThread extends Thread {

  protected InetSocketAddress host;
  
  protected byte[] buffer;
  
  protected PublicKey key;
      
  protected int pastry_port;
  
  private Environment environment;
  private Parameters params;
  
  public NetworkLogUploadThread(Environment env, int port, PublicKey key, InetSocketAddress server) {      
    super("NetworkLogUploadThread");
    this.host = server;
    this.key = key;
    this.pastry_port = port;
    this.environment = env;
    this.params = env.getParameters();
    
    this.buffer = new byte[params.getInt("log_network_buffer_size")];
  }
 
  
  public void run() {
    try {
      int interval = params.getInt("log_network_upload_interval");
      int init_interval = environment.getRandomSource().nextInt(interval / 2) + interval / 2;

      Thread.sleep(init_interval);
    } catch (InterruptedException e) {}
    
    while (true) {
      log(Logger.INFO, "NetworkLogUploadThread Waking up...");
      try {
        sendFiles();
      } catch (IOException e) {
        // should have been logged higher up
      }
      sleep();
    }
  }
  

  protected void sleep() {
    try {
      Thread.sleep(params.getInt("log_network_upload_interval"));
    } catch (InterruptedException e) {
      logException(Logger.WARNING, "Unexpected IE in NetworkLogUploadThread",e);
      throw new RuntimeException("Unexpected InterruptedException",e);
    }
  }
  
  protected void sendFiles() throws IOException {
    final String filename;
    
    if (params.contains("log_network_upload_filename")) {
      filename = params.getString("log_network_upload_filename");
    } else {
      // these two parameters should always have the same value anyway
      filename = params.getString("log_rotate_filename");
    }
    
    File[] files = new File(".").listFiles(new FilenameFilter() {
      public boolean accept(File parent, String file) {
        return file.startsWith(filename + ".");
      }
    });

    for (int i=0; i<files.length; i++) 
      sendFile(files[i], true);
    
    if (params.contains("log_network_upload_other_filenames")) {
      final String other[] = params.getStringArray("log_network_upload_other_filenames");

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
  }
  
  protected byte[] getHeader(File file) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);

      log(Logger.INFO, "writing header for "+file.getName());
      oos.writeObject(InetAddress.getLocalHost().getHostAddress() + ":" + pastry_port + "." + file.getName());
      oos.writeLong(file.length());
      oos.close();
      return baos.toByteArray();
    } catch (Exception e) {
      logException(Logger.WARNING, "ERROR: Could not create header... ", e);
      throw new RuntimeException(e);
    }
  }
  
  protected void sendFile(File file, boolean delete) throws IOException {
    Socket socket = new Socket();
    InputStream in = null;
    GZIPOutputStream out = null;

    try {
      in = new FileInputStream(file);

      socket.connect(host);      
      out = new GZIPOutputStream(new EncryptedOutputStream(key, socket.getOutputStream(),
          params.getInt("p2p_util_encryptedOutputStream_buffer")));
      
      byte[] header = getHeader(file);
      
      out.write(MathUtils.longToByteArray(header.length));
      out.write(header);

      int read = 0;
      
      while ((read = in.read(buffer)) != -1) 
        out.write(buffer, 0, read); 
      
      out.finish();
      out.flush();
      log(Logger.FINE,"Done writing... "+file.getName());
      
      int done = socket.getInputStream().read(new byte[1]);
      log(Logger.FINE,"Done reading... (" + done + ")" + " for "+file.getName());
      
      if (done < 0)
        throw new IOException("Log file was not correctly received - skipping...");
    } catch (IOException e) {
      logException(Logger.WARNING,
          "ERROR: Got exception " + e + " while sending file - aborting!",e);
      throw e;
    } finally {
      try {
        if (socket != null) socket.close();
        if (in != null) in.close();
      } catch (IOException e) {
        logException(Logger.SEVERE, "PANIC: Got exception " + e + " while closing streams!",e);
      }
    }
    
    if (delete) {
      boolean result = file.delete();
      if (! result)
        log(Logger.WARNING,"WARNING: Error deleting log file " + file + " " + file.exists() + " " + result);
    }
  }
  
  private void log(int level, String msg) {
    environment.getLogManager().getLogger(getClass(), "").log(level, msg);
  }

  private void logException(int level, String msg, Throwable t) {
    environment.getLogManager().getLogger(getClass(), "").logException(level, msg, t);
  }
}
