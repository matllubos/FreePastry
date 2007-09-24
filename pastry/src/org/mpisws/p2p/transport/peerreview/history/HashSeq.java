package org.mpisws.p2p.transport.peerreview.history;

public class HashSeq {
  private Hash hash;
  private long seq;
  
  public HashSeq(Hash h, long s) {
    hash = h;
    seq = s;
  }
  
  public Hash getHash() {
    return hash;
  }
  
  public long getSeq() {
    return seq;
  }
  
  
}
