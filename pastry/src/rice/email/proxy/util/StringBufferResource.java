package rice.email.proxy.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;


public class StringBufferResource implements Resource {
  
  public static int MAXIMUM_LENGTH = 7000000;
  
  StringWriter _currentWriter;
  StringBuffer _contentBuffer;
  
  public StringBufferResource() {
  }
  
  public StringBufferResource(String initalValue) {
    _contentBuffer = new StringBuffer(initalValue);
  }
  
  public Writer getWriter() throws IOException {
    _currentWriter = new LimitedStringWriter(MAXIMUM_LENGTH);
    
    return _currentWriter;
  }
  
  public InputStream getInputStream() throws IOException {
    closeInput();
    
    return new StringBufferInputStream(_contentBuffer.toString());
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