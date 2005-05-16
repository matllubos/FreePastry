package rice.email.proxy.util;

import java.io.*;

public class StringBufferResource implements Resource {
  
  public static int MAXIMUM_LENGTH = 7000000;
  
  StringWriter _currentWriter;
  StringBuffer _contentBuffer;
  
  public StringBufferResource() {
  }
  
  public String toString() {
    return _contentBuffer.toString();
  }
  
  public StringBufferResource(String initalValue) {
    _contentBuffer = new StringBuffer(initalValue);
  }
  
  public Writer getWriter() throws IOException {
//    _currentWriter = new LimitedStringWriter(MAXIMUM_LENGTH);
    _currentWriter = new StringWriter();
    
    return _currentWriter;
  }
  
  public InputStream getInputStream() throws IOException {
    closeInput();
    
    return new ByteArrayInputStream(_contentBuffer.toString().getBytes());
  }
  
  public Reader getReader() throws IOException {
    closeInput();
    
    return new StringReader(_contentBuffer.toString());
  }
  
  private void closeInput() throws IOException {
    if (_currentWriter != null) {
      _contentBuffer = _currentWriter.getBuffer();
      _currentWriter = null;
    }
    
    if (_contentBuffer == null)
      throw new IOException("No content has been written");
  }
  
  public long getSize() {       
    try {
      closeInput();
    } catch (IOException e) {
      return 0;
    }
    
    return _contentBuffer.toString().length();
  }
  
  public void delete() {
    _contentBuffer = null;
    _currentWriter = null;
  }
}