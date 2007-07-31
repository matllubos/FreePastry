package org.mpisws.p2p.transport.util;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class FileOutputBuffer extends DataOutputStream implements OutputBuffer {

  public FileOutputBuffer(File f) throws FileNotFoundException {
    super(new FileOutputStream(f));
  }

  public FileOutputBuffer(String fileName) throws FileNotFoundException {
    super(new FileOutputStream(fileName));
  }

  public int bytesRemaining() {
    return Integer.MAX_VALUE;
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
}
