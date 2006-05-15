/*
 * Created on Mar 30, 2006
 */
package rice.p2p.util.rawserialization;

import java.io.*;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class SimpleOutputBuffer extends DataOutputStream implements OutputBuffer {
  ByteArrayOutputStream baos;

  public SimpleOutputBuffer() {
    super(new ByteArrayOutputStream());    
    baos = (ByteArrayOutputStream)out;
  }
  public void writeByte(byte v) throws IOException {
    this.write(v);
  }

  public void writeChar(char v) throws IOException {
    writeChar((int) v);
  }

  public void writeShort(short v) throws IOException {
    writeShort((int) v);
  }
  
  public int bytesRemaining() {
    return Integer.MAX_VALUE;
  }
  
  public byte[] getBytes() {
    return baos.toByteArray();    
  }
  
  public int getWritten() {
    return written; 
  }  
}
