package org.mpisws.p2p.transport.identity;

public interface NodeChangeStrategy<UpperIdentifier, LowerIdentifier> {
  boolean canChange(UpperIdentifier oldDest, UpperIdentifier newDest, LowerIdentifier i);
}
