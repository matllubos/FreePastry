package org.mpisws.p2p.transport.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;

import rice.environment.logging.Logger;

/**
 * Just logs the problems.
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 * @param <E>
 */
public class DefaultErrorHandler<Identifier> implements
    ErrorHandler<Identifier> {
  public int NUM_BYTES_TO_PRINT = 8;
  
  Logger logger;
  public DefaultErrorHandler(Logger logger) {
    this.logger = logger;
  }
  
  public void receivedUnexpectedData(Identifier id, byte[] bytes, int pos, Map<String, Integer> options) {
    if (logger.level <= Logger.WARNING) {
      // make this pretty
      String s = "";
      int numBytes = NUM_BYTES_TO_PRINT;
      if (bytes.length < numBytes) numBytes = bytes.length;
      for (int i = 0; i < numBytes; i++) {
        s+=bytes[i]+","; 
      }
      logger.log("Unexpected data from "+id+" "+s);
    }
  }

  public void receivedException(Identifier i, Throwable error) {
    if (logger.level <= Logger.WARNING) {      
      logger.logException(i == null ? null : i.toString(), new RuntimeException(error));
    }
  }

}
