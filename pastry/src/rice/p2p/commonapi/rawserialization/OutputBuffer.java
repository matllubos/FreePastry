/*
 * Created on Feb 16, 2006
 */
package rice.p2p.commonapi.rawserialization;

import java.io.IOException;

public interface OutputBuffer {
  void   write(byte[] b, int off, int len) throws IOException;
  void   writeBoolean(boolean v) throws IOException;
  void   writeByte(byte v) throws IOException;
  void   writeChar(char v) throws IOException;
  void  writeDouble(double v) throws IOException;
  void  writeFloat(float v) throws IOException;
  void  writeInt(int v) throws IOException;
  void  writeLong(long v) throws IOException;
  void  writeShort(short v) throws IOException;
  void  writeUTF(String str) throws IOException; // based on java's modified UTF format
  
  int bytesRemaining(); // how much space is left in the buffer
}
