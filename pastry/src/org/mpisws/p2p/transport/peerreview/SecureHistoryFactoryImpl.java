package org.mpisws.p2p.transport.peerreview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import rice.environment.logging.Logger;

public class SecureHistoryFactoryImpl implements SecureHistoryFactory {
  public static final int HASH_LENGTH = 20;
  
  Logger logger;
  
  public SecureHistory create(String name, long baseSeq, Hash baseHash) throws IOException {
    SecureHistoryImpl history = new SecureHistoryImpl(name, false, logger);
    
    /* Write the initial record to the index file. The data file remains empty. */
    history.reset(baseSeq, baseHash);

    return history;
  }

  public int getHashSizeBytes() {
    return HASH_LENGTH;
  }

  public SecureHistory open(String name, boolean readonly) throws IOException {
    return new SecureHistoryImpl(name, readonly, logger);
  }

}
