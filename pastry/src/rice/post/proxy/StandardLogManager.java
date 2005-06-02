package rice.post.proxy;

import java.io.*;

import rice.environment.params.Parameters;
import rice.proxy.*;

public class StandardLogManager extends LogManager {
  
  protected FileOutputStream fos;
  
  public StandardLogManager(Parameters parameters) {
    try {
      this.fos = new FileOutputStream(parameters.getString("standard_output_redirect_filename"), 
                                      parameters.getBoolean("standard_output_redirect_append"));
    } catch (FileNotFoundException e) {
      System.err.println("ERROR: Could not find  " + e);
    }
  }
  
  public void write(int b) throws IOException {
    fos.write(b);
  }
  
  public void write(byte b[]) throws IOException {
    fos.write(b);
  }
  
  public void write(byte b[], int off, int len) throws IOException {
    fos.write(b, off, len);
  }
  
  public void flush() throws IOException {
    fos.flush();
  }
  
  public void close() throws IOException {
    fos.close();
  }
  
}
