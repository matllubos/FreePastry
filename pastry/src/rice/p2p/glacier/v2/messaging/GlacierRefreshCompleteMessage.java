package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;

public class GlacierRefreshCompleteMessage extends GlacierMessage {

  protected VersionKey[] keys;
  protected int[] updates;

  public GlacierRefreshCompleteMessage(int uid, VersionKey[] keys, int[] updates, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.keys = keys;
    this.updates = updates;
  }

  public int numKeys() {
    return keys.length;
  }

  public VersionKey getKey(int index) {
    return keys[index];
  }

  public long getUpdates(int index) {
    return updates[index];
  }

  public String toString() {
    return "[GlacierRefreshComplete for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
}

