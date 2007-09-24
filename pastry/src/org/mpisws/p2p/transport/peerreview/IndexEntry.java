/**
 * 
 */
package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class IndexEntry {
  long seq;
  short fileIndex;
  /**
   * The size in the dataFile
   */
  short sizeInFile;
  byte type;
  Hash contentHash;
  Hash nodeHash;
  
  public IndexEntry(long seq) {
    this.seq = seq;
  }

  public IndexEntry(long seq, short index, byte type, short size, Hash contentHash, Hash nodeHash) {
    this.seq = seq;
    this.fileIndex = index;
    this.type = type;
    this.sizeInFile = size;
    this.contentHash = contentHash;
    this.nodeHash = nodeHash;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(seq);
    buf.writeShort(fileIndex);
    buf.writeShort(sizeInFile);
    buf.writeByte(type);
    contentHash.serialize(buf);
    nodeHash.serialize(buf);
  }
}