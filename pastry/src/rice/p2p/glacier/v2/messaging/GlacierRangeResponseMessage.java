package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierRangeResponseMessage extends GlacierMessage {
  protected IdRange commonRange;

  public GlacierRangeResponseMessage(int uid, IdRange commonRange, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.commonRange = commonRange;
  }

  public IdRange getCommonRange() {
    return commonRange;
  }

  public String toString() {
    return "[GlacierRangeResponse to UID#" + getUID() + ", range="+commonRange+"]";
  }
}

