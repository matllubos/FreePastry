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
public class NetworkLogUploadTask extends TimerTask {

  protected InetSocketAddress host;
  
  protected byte[] buffer;
  
  protected PublicKey key;
      
  protected int pastry_port;
  
  private Environment environment;
  private Parameters params;
  
  public NetworkLogUploadTask(Environment env, int port, PublicKey key, InetSocketAddress server) {      
    this.host = server;
    this.key = key;
    this.pastry_port = port;
    this.environment = env;
    this.params = env.getParameters();
    
    this.buffer = new byte[params.getInt("log_network_buffer_size")];
  }
 
  
  public void run() {
    sendFiles();
  }
  
  protected void sendFiles() {
    String file;
    
    if (params.contains("log_network_upload_filename")) {
      file = params.getString("log_network_upload_filename");
    } else {
      // these two parameters should always have the same value anyway
      file = params.getString("log_rotate_filename");
    }
    
    File[] files = new File(".").listFiles(new FilenameFilter() {
      public boolean accept(File parent, String file) {
        return file.startsWith(file + ".");
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
      out = new GZIPOutputStream(new EncryptedOutputStream(key, socket.getOutputStream(),
          params.getInt("p2p_util_encryptedOutputStream_buffer")));
      
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
      environment.getLogManager().getLogger(NetworkLogUploadTask.class, null).logException(Logger.WARNING,
          "ERROR: Got exception " + e + " while sending file - aborting!",e);
      return;
    } finally {
      try {
        if (socket != null) socket.close();
        if (in != null) in.close();
      } catch (IOException e) {
        System.err.println("PANIC: Got exception " + e + " while closing streams!");
      }
    }
    
    if (delete) {
      boolean result = file.delete();
      if (! result)
        System.out.println("WARNING: Error deleting log file " + file + " " + file.exists() + " " + result);
    }
  }
}
