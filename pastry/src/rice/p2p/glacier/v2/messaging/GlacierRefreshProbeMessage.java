package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierRefreshProbeMessage extends GlacierMessage {
  protected Id requestedId;

  public GlacierRefreshProbeMessage(int uid, Id requestedId, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.requestedId = requestedId;
  }

  public Id getRequestedId() {
    return requestedId;
  }

  public String toString() {
    return "[GlacierRefreshProbe #"+getUID()+" for " + requestedId + "]";
  }
}

