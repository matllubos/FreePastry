package rice.p2p.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class RandomAccessFileIOBuffer extends RandomAccessFile implements
    InputBuffer, OutputBuffer {

  public RandomAccessFileIOBuffer(File file, String mode) throws FileNotFoundException {
    super(file, mode);
  }

  public RandomAccessFileIOBuffer(String name, String mode) throws FileNotFoundException {
    super(name, mode);
  }

//  public int bytesRemaining() {
//    try {
//      return this.available();
//    } catch (IOException ioe) {
//      if (logger.level <= Logger.WARNING) logger.logException("error getting available bytes for "+this+".",ioe);
//      return -1;
//    }
//  }

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
