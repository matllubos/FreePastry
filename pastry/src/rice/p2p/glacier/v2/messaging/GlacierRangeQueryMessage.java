package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierRangeQueryMessage extends GlacierMessage {
  protected IdRange requestedRange;

  public GlacierRangeQueryMessage(int uid, IdRange requestedRange, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedRange = requestedRange;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public String toString() {
    return "[GlacierRangeQuery #"+getUID()+" for " + requestedRange + "]";
  }
}

