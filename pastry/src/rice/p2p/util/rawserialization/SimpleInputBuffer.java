/*
 * Created on Mar 30, 2006
 */
package rice.p2p.util.rawserialization;

import java.io.*;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public class SimpleInputBuffer extends DataInputStream implements InputBuffer {
  ByteArrayInputStream bais;
  
  public SimpleInputBuffer(byte[] bytes) {
    this(bytes,0); 
  }
  public SimpleInputBuffer(byte[] bytes, int skip) {
    super(new ByteArrayInputStream(bytes));
    bais = (ByteArrayInputStream)this.in;
    bais.skip(skip);
  }
  
//  public short peakShort() throws IOException {
//    bais.mark(2);
//    short temp = readShort();
//    bais.reset();
//    return temp;
//  }
  
  public int bytesRemaining() {
    try {
      return this.available();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    return -1;
  }  
}
