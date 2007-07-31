package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;

public interface SecureHistoryFactory {

//  SecureHistory createTemp(long baseSeq, const unsigned char *baseHash) { return create(NULL, baseSeq, baseHash); };
  /**
   * Creates a new history (aka log). Histories are stored as two files: The 'index' file has a 
   * fixed-size record for each entry, which contains the sequence number, content and node
   * hashes, as well as an index into the data file. The 'data' file just contains the raw
   * bytes for each entry. Note that the caller must specify the node hash and the sequence
   * number of the first log entry, which forms the base of the hash chain.
   */
  SecureHistory create(String name, long baseSeq, Hash baseHash) throws IOException;
  SecureHistory open(String name, boolean readonly) throws IOException;
  int getHashSizeBytes();
}
