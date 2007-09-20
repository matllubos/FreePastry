package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface IndexEntryFactory {

  IndexEntry build(InputBuffer indexFile) throws IOException;

  int getSerializedSize();

}
