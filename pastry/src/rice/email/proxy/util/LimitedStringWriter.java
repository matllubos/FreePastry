package rice.email.proxy.util;

import java.io.*;

public class LimitedStringWriter extends StringWriter {
  
  /**
   * The capacity of the writer
   */
  protected int capacity;
  
  /**
   * Constructor which takes a maximum capacity
   * 
   * @param capacity The capacity
   */
  public LimitedStringWriter(int capacity) {
    this.capacity = capacity;
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param c The character
   */
  public void write(int c) {
    if (getBuffer().length() + 1 > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(c);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param c The array
   * @param off The offset
   * @param len The length
   */
  public void write(char[] c, int off, int len) {
    if (getBuffer().length() + len > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(c, off, len);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param str The string
   */
  public void write(String str) {
    if (getBuffer().length() + str.length() > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(str);
  }
  
  /**
   * First checks to see if this will put us over capacity, and if 
   * so, throws a StringWriterOverflowException. Otherwise, proceeds
   * as normal
   *
   * @param str The string
   * @param off The offset
   * @param len The length
   */
  public void write(String str, int off, int len) {
    if (getBuffer().length() + len > capacity) 
      throw new StringWriterOverflowException();
    
    super.write(str, off, len);
  }
}