package rice.post.proxy;

import java.io.*;
import rice.proxy.*;

public class ConsoleLogManager extends LogManager {
    
  public ConsoleLogManager(Parameters parameters) {
  }
  
  public void write(int b) throws IOException {
    System.out.write(b);
  }
  
  public void write(byte b[]) throws IOException {
    System.out.write(b);
  }
  
  public void write(byte b[], int off, int len) throws IOException {
    System.out.write(b, off, len);
  }
  
  public void flush() throws IOException {
    System.out.flush();
  }
  
  public void close() throws IOException {
    System.out.close();
  }
  
}
