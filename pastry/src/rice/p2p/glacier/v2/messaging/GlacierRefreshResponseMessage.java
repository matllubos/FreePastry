package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierRefreshResponseMessage extends GlacierMessage {
  protected IdRange range;
  protected boolean online;

  public GlacierRefreshResponseMessage(int uid, IdRange range, boolean online, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.range = range;
    this.online = online;
  }

  public IdRange getRange() {
    return range;
  }
  
  public boolean isOnline() {
    return online;
  }

  public String toString() {
    return "[GlacierRefreshResponse for "+range+", online="+online+"]";
  }
}

