package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;

public class GlacierNeighborRequestMessage extends GlacierMessage {

  protected IdRange requestedRange;

  public GlacierNeighborRequestMessage(int uid, IdRange requestedRange, NodeHandle source, Id dest) {
    super(uid, source, dest, false);

    this.requestedRange = requestedRange;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public String toString() {
    return "[GlacierNeighborRequest for " + requestedRange +"]";
  }
}

