package rice.post.proxy;

import java.io.*;

public abstract class LogManager extends OutputStream {
  
  public abstract void write(int b) throws IOException;

  public abstract void write(byte b[]) throws IOException;
 
  public abstract void write(byte b[], int off, int len) throws IOException;

  public abstract void flush() throws IOException;
    
  public abstract void close() throws IOException;
  
}
  