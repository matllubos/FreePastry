package rice.p2p.aggregation.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class AggregationTimeoutMessage extends AggregationMessage {

  public AggregationTimeoutMessage(int uid, NodeHandle local) {
    super(uid, local, local.getId());
  }

  public String toString() {
    return "[AggregationTimeoutMessage "+getUID()+"]";
  }
}

