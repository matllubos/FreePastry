package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierFetchMessage extends GlacierMessage {
  protected FragmentKey key;

  public GlacierFetchMessage(int uid, FragmentKey key, NodeHandle source, Id dest) {
    super(uid, source, dest, false);

    this.key = key;
  }

  public FragmentKey getKey() {
    return key;
  }

  public String toString() {
    return "[GlacierFetch for " + key + "]";
  }
}

