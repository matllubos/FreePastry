
package rice.p2p.util;

import java.io.*;
import java.security.*;
import java.util.*;

/**
* @(#) EncryptedInputStream.java
 *
 * Class which serves as an input stream and transparently dencrypts
 * the stream data with the given private key.  It does this by reading
 * a symmetric key, k, and then decrypting k under the public key.  
 * The header read from the stream therefore looks like
 *
 * 4 bytes - length(e(k))
 * e(k)
 * chunks of upper-level data
 * 
 * where each chunk is length(data) [4 bytes] followed by data
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class EncryptedInputStream extends InputStream {
  
  // the public key
  protected PrivateKey privateKey;
  
  // the decrypted symmetric key
  protected byte[] key;
  
  // the actual underlying stream
  protected DataInputStream stream;
  
  // any buffered data from the stream
  protected byte[] buffer;
  
  // the amount of buffered data which has been returned
  protected int bufferLength;
  
  /**
   * Builds an encrypted inputstream given a private key to 
   * decrypt thing under
   *
   * @param key The key
   * @param stream The underlying stream
   */
  public EncryptedInputStream(PrivateKey privateKey, InputStream stream) throws IOException {
    this.privateKey = privateKey;
    this.stream = new DataInputStream(stream);
    
    readHeader();
  }
  
  /**
    * Utility method which reads the header information
   */
  private void readHeader() throws IOException {
    byte[] enckey = new byte[stream.readInt()];
    stream.readFully(enckey);
    
    this.key = SecurityUtils.decryptAsymmetric(enckey, privateKey);
  }
  
  /**
   * Reads the next byte of data from the input stream. T
   *
   * @return the next byte of data, or <code>-1</code> if the end of the
   *         stream is reached.
   */
  public int read() throws IOException {
    if ((buffer != null) && (bufferLength < buffer.length)) {
      bufferLength++;
      
      return buffer[bufferLength-1] & 0xff;
    } else {
      readBuffer();
      return read();
    }
  }
  
  /**
   * Reads up to <code>len</code> bytes of data from the input stream into
   * an array of bytes.  An attempt is made to read as many as
   * <code>len</code> bytes, but a smaller number may be read, possibly
   * zero. The number of bytes actually read is returned as an integer.
   *
   * @param      b     the buffer into which the data is read.
   * @param      off   the start offset in array <code>b</code>
   *                   at which the data is written.
   * @param      len   the maximum number of bytes to read.
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   */
  public int read(byte b[], int off, int len) throws IOException {
    if ((buffer != null) && (bufferLength < buffer.length)) {
      int l = (len > available() ? available() : len);
      System.arraycopy(buffer, bufferLength, b, off, l);
      bufferLength += l;
      
      return l;
    } else {
      readBuffer();
      return read(b, off, len);
    }
  }
  
  /**
   * Internal method which reads in the next chunk of buffered data
   */
  protected void readBuffer() throws IOException {    
    byte[] encdata = new byte[stream.readInt()];
    stream.readFully(encdata);
    
    this.buffer = SecurityUtils.decryptSymmetric(encdata, key);
    this.bufferLength = 0;
  }

  /**
   * Returns the number of bytes that can be read (or skipped over) from
   * this input stream without blocking by the next caller of a method for
   * this input stream.  The next caller might be the same thread or or
   * another thread.
   *
   * @return     the number of bytes that can be read from this input stream
   *             without blocking.
   * @exception  IOException  if an I/O error occurs.
   */
  public int available() throws IOException {
    if (buffer == null)
      return 0;
    else
      return buffer.length - bufferLength;
  }
  
  /**
   * Closes this input stream and releases any system resources associated
   * with the stream.
   *
   * @exception  IOException  if an I/O error occurs.
   */
  public void close() throws IOException {
    stream.close();
  }
}
  
