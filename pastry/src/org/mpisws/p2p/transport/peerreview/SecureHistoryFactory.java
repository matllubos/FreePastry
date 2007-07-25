package org.mpisws.p2p.transport.peerreview;

public interface SecureHistoryFactory {

//  SecureHistory createTemp(long baseSeq, const unsigned char *baseHash) { return create(NULL, baseSeq, baseHash); };
  SecureHistory create(String name, long baseSeq, Hash baseHash);
  SecureHistory open(String name, boolean readonly);
  int getHashSizeBytes();
}
