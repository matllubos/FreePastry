/*
 * Created on Feb 16, 2006
 */
package rice.p2p.commonapi.rawserialization;

import java.io.IOException;

public interface InputBuffer {
  int read(byte[] b, int off, int len) throws IOException;
  int read(byte[] b) throws IOException;
  boolean readBoolean() throws IOException;
  byte readByte() throws IOException;
  char readChar() throws IOException;
  double readDouble() throws IOException;
  float readFloat() throws IOException;
  int readInt() throws IOException;
  long readLong() throws IOException;
  short readShort() throws IOException;
  String readUTF() throws IOException; // based on java's modified UTF format
  
  int bytesRemaining(); // how much space is left in the buffer
}
