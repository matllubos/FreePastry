package org.mpisws.p2p.transport.peerreview.history;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface HashDeserializer {

  Hash build(InputBuffer buf);

  int getSerizlizedSize(); // 20 by default

  Hash getEmpty();

}
