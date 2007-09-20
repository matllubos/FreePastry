/**
 * 
 */
package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

class IndexEntry {
  long seq;
  short fileIndex;
  short sizeInFile;
  short type;
  Hash contentHash;
  Hash nodeHash;
  
  public IndexEntry(long seq, short index, short type, short size, Hash contentHash, Hash nodeHash) {
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
    buf.writeShort(type);
    contentHash.serialize(buf);
    nodeHash.serialize(buf);
  }
}