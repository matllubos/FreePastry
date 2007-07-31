package org.mpisws.p2p.transport.peerreview;

import rice.p2p.commonapi.rawserialization.OutputBuffer;

public interface Hash {

  /**
   * Write self to file.
   * 
   * @param buf
   */
  void serialize(OutputBuffer buf);

}
