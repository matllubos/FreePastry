package org.mpisws.p2p.transport.peerreview.history;

public interface HashPolicy {

  boolean hashEntry(short type, byte[] buffer, long sizeInFile);

}
