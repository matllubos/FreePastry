package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierRangeForwardMessage extends GlacierMessage {
  protected IdRange requestedRange;
  protected NodeHandle requestor;

  public GlacierRangeForwardMessage(int uid, IdRange requestedRange, NodeHandle requestor, NodeHandle source, Id dest) {
    super(uid, source, dest, false);

    this.requestedRange = requestedRange;
    this.requestor = requestor;
  }

  public IdRange getRequestedRange() {
    return requestedRange;
  }

  public NodeHandle getRequestor() {
    return requestor;
  }

  public String toString() {
    return "[GlacierRangeForward #"+getUID()+" for " + requestedRange + " by " + requestor + "]";
  }
}

