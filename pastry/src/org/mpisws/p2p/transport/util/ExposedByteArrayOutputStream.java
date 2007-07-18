package org.mpisws.p2p.transport.util;

import java.io.ByteArrayOutputStream;

public class ExposedByteArrayOutputStream extends ByteArrayOutputStream {
  public ExposedByteArrayOutputStream(int size) {      
    super(size);
  }

  public ExposedByteArrayOutputStream() {      
    super();
  }

  public byte[] buf() {
    return buf;
  }
}

//static class ExposedDataOutputStream extends DataOutputStream implements
//    OutputBuffer {
//  int capacity;
//
//  public ExposedDataOutputStream(OutputStream sub, int capacity) {
//    super(sub);
//    this.capacity = capacity;
//  }
//
//  public int bytesRemaining() {
//    return capacity - size();
//  }
//
//  public void reset() {
//    written = 0;
//  }
//
//  public int bytesWritten() {
//    return written; 
//  }
//  
//  public void writeByte(byte v) throws IOException {
//    this.write(v);
//  }
//
//  public void writeChar(char v) throws IOException {
//    writeChar((int) v);
//  }
//
//  public void writeShort(short v) throws IOException {
//    writeShort((int) v);
//  }
//}
