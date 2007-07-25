package org.mpisws.p2p.transport.peerreview;

import java.io.File;

/**
 * The following class implements PeerReview's log. A log entry consists of
 * a sequence number, a type, and a string of bytes. On disk, the log is
 * stored as two files: An index file and a data file.
 * 
 * @author Jeff Hoye
 * @author Andreas Haeberlen
 */
public class SecureHistoryImpl implements SecureHistory {

  boolean pointerAtEnd;
  IndexEntry topEntry;
  long baseSeq;
  long nextSeq;
  int numEntries;
//  FILE *indexFile;
//  FILE *dataFile;
  boolean readonly;
  
  class IndexEntry {
    long seq;
    int fileIndex;
    int sizeInFile;
    int type;
//    unsigned char contentHash[HASH_LENGTH];
//    unsigned char nodeHash[HASH_LENGTH];
  }
  
  public SecureHistoryImpl(File indexFile, File dataFile, boolean readonly2) {
    // TODO Auto-generated constructor stub
  }

  public long getBaseSeq() {
    return baseSeq;
  }

  public long getLastSeq() {
    return topEntry.seq;
  }

  public int getNumEntries() {
    return numEntries;
  }

}
