package rice.email.proxy.util;

import java.io.*;

public class StringBufferResource implements Resource {
  
  public static int MAXIMUM_LENGTH = 7000000;
  
  /**
   * The buffers used in the resource 
   */
  protected StringWriter writer;
  protected StringBuffer buffer;
  
  public StringBufferResource() {
  }
  
  public StringBufferResource(String initalValue) {
    buffer = new StringBuffer(initalValue);
  }
  
  public Writer getWriter() throws IOException {
//    writer = new LimitedStringWriter(MAXIMUM_LENGTH);
    writer = new StringWriter();
    
    return writer;
  }
  
  public InputStream getInputStream() throws IOException {
    closeInput();
    
    return new InputStream() {
      int pos = 0;
      
      public int read() {
        return (pos < buffer.length() ? (int) buffer.charAt(pos++) & 0xFF : -1);
      }
      
      public int read(byte[] buf, int off, int len) {
        if (pos >= buffer.length())
          return -1;
        
        if (len > buffer.length() - pos)
          len = buffer.length() - pos;
        
        for (int i=0; i<len; i++)
          buf[off+i] = (byte) buffer.charAt(pos++);
        
        return len;
      }
    };
  }
  
  public Reader getReader() throws IOException {
    closeInput();
    
    return new InputStreamReader(getInputStream());
  }
  
  private void closeInput() throws IOException {
    if (writer != null) {
      buffer = writer.getBuffer();
      writer = null;
    }
    
    if (buffer == null)
      throw new IOException("No content has been written");
  }
  
  public long getSize() {       
    try {
      closeInput();
    } catch (IOException e) {
      return 0;
    }
    
    return buffer.length();
  }
  
  public void delete() {
    buffer = null;
    writer = null;
  }
}