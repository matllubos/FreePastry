/**
 * 
 */
package org.mpisws.p2p.transport.peerreview.history;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public class IndexEntry {
  /**
   * A unique sequence number.
   */
  long seq;
  
  /**
   * Where in the file the entry is located.
   */
  long fileIndex;
  
  /**
   * The size of the entry in the dataFile
   */
  int sizeInFile;
  /**
   * This is the operation type.  Could be a Past get/put, or msg send/receive etc.
   */
  short type;
  
  /**
   * The Hash of the content
   * 
   * H(full content)
   */
  Hash contentHash;
  /**
   * H(prevNode.nodeHash, seq, type, contentHash)
   */
  Hash nodeHash;
  
  public IndexEntry(long seq) {
    this.seq = seq;
  }

  public IndexEntry(long seq, long index, short type, int size, Hash contentHash, Hash nodeHash) {
    this.seq = seq;
    this.fileIndex = index;
    this.type = type;
    this.sizeInFile = size;
    this.contentHash = contentHash;
    this.nodeHash = nodeHash;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(seq);
    buf.writeLong(fileIndex);
    buf.writeInt(sizeInFile);
    buf.writeShort(type);
    contentHash.serialize(buf);
    nodeHash.serialize(buf);
  }

  public Hash getContentHash() {
    return contentHash;
  }

  public long getFileIndex() {
    return fileIndex;
  }

  public Hash getNodeHash() {
    return nodeHash;
  }

  public long getSeq() {
    return seq;
  }

  public int getSizeInFile() {
    return sizeInFile;
  }

  public short getType() {
    return type;
  }
  
  public String toString() {
    return "IE{#"+seq+" t:"+type+" s:"+sizeInFile+"}";
  }
}