package org.mpisws.p2p.transport.util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;

public class FileInputBuffer extends DataInputStream implements InputBuffer {
  Logger logger;
  File file;
  
  public FileInputBuffer(File f, Logger logger) throws FileNotFoundException {
    super(new FileInputStream(f));
    this.file = f;
  }

  public FileInputBuffer(String fileName, Logger logger) throws FileNotFoundException {
    this(new File(fileName), logger);
  }

  public int bytesRemaining() {
    try {
      return this.available();
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.logException("error getting available bytes for "+this+".",ioe);
      return -1;
    }
  }

  public String toString() {
    return "FIB{"+file+"}";
  }
  
}
