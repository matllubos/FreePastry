package rice.p2p.aggregation.messaging;

import java.io.IOException;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.*;

public class AggregationTimeoutMessage extends AggregationMessage {
  public static final short TYPE = 1;
  
  public AggregationTimeoutMessage(int uid, NodeHandle local) {
    super(uid, local, local.getId());
  }

//  public AggregationTimeoutMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
//    super(buf, endpoint);
//  }

  public String toString() {
    return "[AggregationTimeoutMessage "+getUID()+"]";
  }

//  public short getType() {
//    return TYPE;
//  }
}

