package org.mpisws.p2p.transport.peerreview;

import java.io.File;

public class SecureHistoryFactoryImpl implements SecureHistoryFactory {
  public static final int HASH_LENGTH = 20;
  
  public SecureHistory create(String name, long baseSeq, Hash baseHash) {
    // TODO: implement
    return null;
  }

  public int getHashSizeBytes() {
    return HASH_LENGTH;
  }

  public SecureHistory open(String name, boolean readonly) {
    File indexFile = new File(name+".index");      
    File dataFile = new File(name+".data");
    
    return new SecureHistoryImpl(indexFile, dataFile, readonly);
  }

}
